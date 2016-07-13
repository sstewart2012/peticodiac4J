package ca.uwaterloo.simplex.solver;

import java.util.Arrays;
import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import ca.uwaterloo.shediac.KernelMgr;
import ca.uwaterloo.shediac.KernelMgr.DeviceType;
import ca.uwaterloo.shediac.memory.Buffer;
import ca.uwaterloo.shediac.memory.Memory;
import ca.uwaterloo.simplex.bounds.DeviceBounds;

/**
 * A device-accelerated implementation of the AbstractSolver for general simplex. This
 * implementation will call compute kernels in order to carry out the core operations: checkBounds,
 * findSuitable, pivot, and updateAssignment.
 * 
 * @author Steven Stewart
 */
public class DeviceSolver extends AbstractSolver {

  private final DeviceType type;
  private final KernelMgr mgr;

  private final DeviceBounds devBounds;
  private final int groupId;
  private final Memory memOutput;
  private final Memory memTableau;
  private final Memory memColToVar;
  private final Memory memRowToVar;
  private final Memory memVarToTableau;
  private final Memory memPartialSums;
  private final int[] output = new int[1];
  private final byte[] flags;

  private final String cuFilename = "kernels/generalSimplex.cu";
  private final String clFilename = "kernels/generalSimplex.cl";

  private final String[] kernelNames = new String[] {"check_bounds", "find_suitable",
      "find_suitable_complete", "pivot_update_inner", "pivot_update_row", "pivot_update_column",
      "update_assignment", "update_assignment_complete"};

  private final HashMap<String, Integer> kernels = new HashMap<>();

  private final int workgroupSize;
  private final int numLaunches;
  private final int numVarsPerLaunch;

  /**
   * DeviceSolver
   * 
   * @param maxNumBasic
   * @param numNonbasic
   * @param type
   * @param platformId
   * @param deviceId
   * @param enableExceptions
   */
  DeviceSolver(final int maxNumBasic, final int numNonbasic, final DeviceType type,
      final int platformId, final int deviceId, final boolean enableExceptions) {
    super(maxNumBasic, numNonbasic, BoundsType.Device);

    this.type = type;
    this.devBounds = ((DeviceBounds) bounds);
    final String filename = type == DeviceType.CUDA ? cuFilename : clFilename;

    // Set flags indicating which vars are "basic"
    flags = new byte[numVars];
    for (int i = numNonbasic; i < numVars; i++)
      flags[i] = 1;

    // Create kernel manager and group
    mgr = new KernelMgr();
    groupId = mgr.createKernelGroup(type, platformId, deviceId, enableExceptions);

    // Configuration
    workgroupSize = (int) mgr.getDevice(groupId).maxWorkGroupSize();
    numVarsPerLaunch = mgr.getDevice(groupId).computeUnits() * workgroupSize;
    numLaunches = (numVars + workgroupSize - 1) / workgroupSize;

    // Allocate output array for partial sums used during updateAssignment
    final int numWorkgroups = (numColumns + workgroupSize - 1) / workgroupSize;

    // Allocate device memory
    memTableau = mgr.allocateDevice(groupId, maxNumRows * numColumns * Float.BYTES);
    memOutput = mgr.allocateDeviceFromHost(groupId, output);
    memColToVar = mgr.allocateDeviceFromHost(groupId, colToVar);
    memRowToVar = mgr.allocateDeviceFromHost(groupId, rowToVar);
    memVarToTableau = mgr.allocateDeviceFromHost(groupId, varToTableau);
    memPartialSums = mgr.allocateDevice(groupId, numWorkgroups * Float.BYTES);

    // Allocate device memory
    final int size = Float.BYTES * numVars;
    devBounds.memAssigns = mgr.allocateDevice(groupId, size);
    devBounds.memLower = mgr.allocateDevice(groupId, size);
    devBounds.memUpper = mgr.allocateDevice(groupId, size);
    devBounds.memFlags = mgr.allocateDeviceFromHost(groupId, flags);

    // Initialize assignments on device to 0
    for (int i = 0; i < numVars; i++)
      bounds.setAssignment(i, 0.0f);

    // Create kernels
    for (final String name : kernelNames)
      kernels.put(name, mgr.addKernel(groupId, filename, name));
  }

  /**
   * Prepares the kernels prior to carrying out the solving procedure. This includes: (1) adding
   * kernel arguments; (2) the initial copying of the tableau from host to device.
   */
  @Override
  public void preSolve() {
    // Add arguments for checkBounds kernel
    {
      final Integer[] scalars = new Integer[] {numVars, 0};
      final Memory[] buffers = new Memory[] {devBounds.memLower, devBounds.memUpper,
          devBounds.memAssigns, devBounds.memFlags, memOutput};
      addArgs(kernels.get("check_bounds"), scalars, buffers);
    }
    // Add arguments for findSuitable kernel
    {
      final Integer[] scalars = new Integer[] {numColumns, 0, 0};
      final Memory[] buffers = new Memory[] {memTableau, devBounds.memLower, devBounds.memUpper,
          devBounds.memAssigns, devBounds.memFlags, memVarToTableau, memOutput};
      addArgs(kernels.get("find_suitable"), scalars, buffers);
    }
    // Add arguments for findSuitableComplete kernel
    {
      final Integer[] scalars = new Integer[] {numColumns, 0, 0};
      final Memory[] buffers = new Memory[] {memTableau, devBounds.memLower, devBounds.memUpper,
          devBounds.memAssigns, memVarToTableau};
      addArgs(kernels.get("find_suitable_complete"), scalars, buffers);
    }
    // Add arguments for pivotUpdateInner kernel
    {
      final Integer[] scalars = new Integer[] {0, 0, 0, numRows, numColumns};
      final Memory[] buffers = new Memory[] {memTableau};
      addArgs(kernels.get("pivot_update_inner"), scalars, buffers);
    }
    // Add arguments for pivotUpdateRow kernel
    {
      final Integer[] scalars = new Integer[] {0, 0, numColumns};
      final Memory[] buffers = new Memory[] {memTableau};
      addArgs(kernels.get("pivot_update_row"), scalars, buffers);
    }
    // Add arguments for pivotUpdateColumn kernel
    {
      final Integer[] scalars = new Integer[] {0, 0, numRows, numColumns};
      final Memory[] buffers = new Memory[] {memTableau};
      addArgs(kernels.get("pivot_update_column"), scalars, buffers);
    }
    // Add arguments for the two updateAssignment kernels
    {
      final Integer[] scalars = new Integer[] {numColumns};
      final Memory[] buffers =
          new Memory[] {memTableau, devBounds.memAssigns, memColToVar, memPartialSums};
      addArgs(kernels.get("update_assignment"), scalars, buffers);
    }
    {
      final Integer[] scalars = {0, 0};
      final Memory[] buffers = new Memory[] {memTableau, devBounds.memAssigns};
      addArgs(kernels.get("update_assignment_complete"), scalars, buffers);
    }

    // Copy tableau from host to device
    // TODO This is inefficient. A block H2D copy should be available.
    for (int i = 0; i < numRows; i++) {
      for (int j = 0; j < numColumns; j++) {
        final int offset = i * numColumns + j;
        memTableau.asFloatMemory().set(offset, tableau[offset]);
      }
    }
  }

  /**
   * Convenience method for adding kernel arguments, which assumes that scalars are added before
   * memory buffer references.
   * 
   * @param kernelId the id of the kernel
   * @param scalars the scalar arguments
   * @param buffers the memory buffer arguments
   */
  private void addArgs(final int kernelId, final Integer[] scalars, final Memory[] buffers) {
    if (scalars != null)
      for (final Integer val : scalars)
        mgr.addArgumentScalar(groupId, kernelId, val);
    if (buffers != null)
      for (final Memory m : buffers)
        mgr.addArgument(groupId, kernelId, m);
  }

  @Override
  protected int checkBounds() {
    int kernelId = kernels.get("check_bounds");
    int offset = 0;
    output[0] = bounds.numVars();
    memOutput.copyHtoD();
    for (int i = 0; i < numLaunches; i++, offset += numVarsPerLaunch) {
      mgr.setArgumentScalar(groupId, kernelId, 1, offset);
      mgr.runKernel(groupId, kernelId, new long[] {numVarsPerLaunch, 1, 1},
          new long[] {workgroupSize, 1, 1});
      memOutput.copyDtoH();
      if (output[0] != numVars)
        break;
    }
    final int result = output[0] != numVars ? output[0] : -1;
    Logger.getLogger("Solver").log(Level.FINE, "checkBounds: @ row " + (output[0] - numColumns)
        + ", " + var2str(output[0]) + " is broken");
    return result;
  }

  @Override
  protected int findSuitable(final int brokenIdx) {
    // Launch kernel to find suitable variable
    int kernelId = kernels.get("find_suitable");
    int offset = 0;
    output[0] = numVars;
    mgr.setArgumentScalar(groupId, kernelId, 1, brokenIdx);
    memOutput.copyHtoD();
    for (int i = 0; i < numLaunches; i++, offset += numVarsPerLaunch) {
      mgr.setArgumentScalar(groupId, kernelId, 2, offset);
      mgr.runKernel(groupId, kernelId, new long[] {numVarsPerLaunch, 1, 1},
          new long[] {workgroupSize, 1, 1});
      memOutput.copyDtoH();
      if (output[0] != numVars)
        break;
    }
    final int suitableIdx = output[0] != numVars ? colToVar[output[0]] : -1;

    if (suitableIdx >= 0) {
      // Run second kernel to complete the operation
      kernelId = kernels.get("find_suitable_complete");
      mgr.setArgumentScalar(groupId, kernelId, 1, brokenIdx);
      mgr.setArgumentScalar(groupId, kernelId, 2, suitableIdx);
      mgr.runKernel(groupId, kernelId, new long[] {1, 1, 1}, new long[] {1, 1, 1});
    }
    Logger.getLogger("Solver").log(Level.FINE, "findSuitable: " + var2str(suitableIdx));
    return suitableIdx;
  }

  @Override
  protected void pivot(int basicIdx, int nonbasicIdx) {
    Logger.getLogger("Solver").log(Level.FINE,
        "pivot: brokenIdx=" + var2str(basicIdx) + " suitableIdx=" + var2str(nonbasicIdx));
    final int pivotRow = varToTableau[basicIdx];
    final int pivotCol = varToTableau[nonbasicIdx];
    final int alphaIdx = pivotRow * numColumns + pivotCol;

    // Save pivot element
    final float alpha = memTableau.asFloatMemory().get(alphaIdx);

    // Update the tableau
    pivotUpdateInner(alpha, pivotRow, pivotCol);
    pivotUpdateRow(alpha, pivotRow);
    pivotUpdateColumn(alpha, pivotCol);

    // Update pivot element
    memTableau.asFloatMemory().set(alphaIdx, 1 / alpha);

    // Swap the basic and nonbasic variables
    colToVar[pivotCol] = basicIdx;
    rowToVar[pivotRow] = nonbasicIdx;
    varToTableau[basicIdx] = pivotCol;
    varToTableau[nonbasicIdx] = pivotRow;

    // Copy update mappings to device
    memColToVar.copyHtoD();
    memRowToVar.copyHtoD();
    memVarToTableau.copyHtoD();
  }

  /** Helper method for pivot operation. */
  private void pivotUpdateInner(final float alpha, final int row, final int col) {
    final int kernelId = kernels.get("pivot_update_inner");
    mgr.setArgumentScalar(groupId, kernelId, 0, alpha);
    mgr.setArgumentScalar(groupId, kernelId, 1, row);
    mgr.setArgumentScalar(groupId, kernelId, 2, col);
    final long global[] = new long[] {numRows, numColumns, 1};
    final long local[] = new long[] {32, 32, 1};
    mgr.runKernel(groupId, kernels.get("pivot_update_inner"), global, local);
  }

  /** Helper method for pivot operation. */
  private void pivotUpdateRow(final float alpha, final int row) {
    final int kernelId = kernels.get("pivot_update_row");
    mgr.setArgumentScalar(groupId, kernelId, 0, alpha);
    mgr.setArgumentScalar(groupId, kernelId, 1, row);
    final long global[] = new long[] {numColumns, 1, 1};
    final long local[] = new long[] {workgroupSize, 1, 1};
    mgr.runKernel(groupId, kernelId, global, local);
  }

  /** Helper method for pivot operation. */
  private void pivotUpdateColumn(final float alpha, final int col) {
    final int kernelId = kernels.get("pivot_update_column");
    mgr.setArgumentScalar(groupId, kernelId, 0, alpha);
    mgr.setArgumentScalar(groupId, kernelId, 1, col);
    final long global[] = new long[] {numRows, 1, 1};
    final long local[] = new long[] {workgroupSize, 1, 1};
    mgr.runKernel(groupId, kernels.get("pivot_update_column"), global, local);
  }

  @Override
  protected void updateAssignment() {
    // TODO fix: currently, only produces correct results if the number of
    // columns is a power of 2
    assert numColumns >= 0 && (numColumns & (numColumns - 1)) == 0;
    for (int i = 0; i < numRows; i++) {
      final Buffer row = memTableau.getDeviceBuffer().withByteOffset(i * numColumns * Float.BYTES);
      updateAssignmentRow(i, row);
    }
  }

  private void updateAssignmentRow(final int rowIdx, final Buffer row) {
    final int kernelId = kernels.get("update_assignment");
    final long global[] = new long[] {numColumns, 1, 1};
    final long local[] = new long[] {workgroupSize, 1, 1};
    final int numGroups = (numColumns + workgroupSize - 1) / workgroupSize;

    // Runs the update_assignment kernel. Each workgroup performs a
    // reduction on its share of the
    // columns, multiplying each element by the current assignment of the
    // variable of the respective
    // column. The result from each workgroup is stored in memOutput.
    mgr.setArgument(groupId, kernelId, 1, row);
    mgr.runKernel(groupId, kernelId, global, local);

    if (numGroups == 1) {
      // If only one workgroup was needed in the first step, then the new
      // assignment is in memOutput[0] and needs to be written to
      // memAssigns[var]
      final float a = memPartialSums.asFloatMemory().get(0);
      devBounds.memAssigns.asFloatMemory().set(rowToVar[rowIdx], a);
      Logger.getLogger("Solver").log(Level.FINE,
          "updateAssignment (" + var2str(rowToVar[rowIdx]) + "): " + a);
      return;
    }

    // Otherwise, we reduce the output of the first reduction step. This
    // second kernel simply
    // reduces the partial sums produced by the first kernel, without having
    // to multiply any
    // values by an assignment.

  }

  private String var2str(final int varIdx) {
    if (varIdx >= numColumns)
      return "s" + (varIdx - numColumns);
    else
      return "x" + varIdx;
  }

  private void updateAssignmentComplete(final int offset, final int rowIdx, final int numItems,
      final Buffer input) {
    final int kernelId = kernels.get("update_assignment_complete");
    mgr.setArgumentScalar(groupId, kernelId, 0, offset);
    mgr.setArgumentScalar(groupId, kernelId, 1, rowToVar[rowIdx]);
    mgr.setArgument(groupId, kernelId, 2, input);
    mgr.runKernel(groupId, kernelId, new long[] {numItems, 1, 1}, new long[] {numItems, 1, 1});
  }

  @Override
  public String toString() {
    return "DeviceSolver [type=" + type + ", groupId=" + groupId + ", kernelNames="
        + Arrays.toString(kernelNames) + "]";
  }

  @Override
  protected float getTableauEntry(int row, int col) {
    return memTableau.asFloatMemory().get(row * numColumns + col);
  }

}
