package ca.uwaterloo.simplex.solver;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import ca.uwaterloo.simplex.bounds.Bounds;
import ca.uwaterloo.simplex.bounds.CpuBounds;
import ca.uwaterloo.simplex.bounds.DeviceBounds;

public abstract class AbstractSolver implements Solver {

  public enum BoundsType {
    CPU, Device
  }

  protected final static Logger logger = Logger.getLogger("Solver");

  protected final Bounds bounds;
  protected int numRows = 0;
  protected final int maxNumRows;
  protected final int numColumns;
  protected final int numVars;
  protected final float[] tableau;
  protected final int[] colToVar;
  protected final int[] rowToVar;
  protected final int[] varToTableau;

  /**
   * 
   * 
   * @param numBasic
   * @param maxNumNonBasic
   * @param type
   */
  public AbstractSolver(final int maxNumBasic, final int numNonbasic, final BoundsType type) {
    if (type == BoundsType.CPU)
      bounds = new CpuBounds(maxNumBasic, numNonbasic, EPSILON);
    else if (type == BoundsType.Device)
      bounds = new DeviceBounds(maxNumBasic, numNonbasic, EPSILON);
    else
      throw new RuntimeException("Invalid bounds");

    maxNumRows = maxNumBasic;
    numColumns = numNonbasic;
    numVars = maxNumBasic + numNonbasic;

    tableau = new float[maxNumBasic * numNonbasic];
    colToVar = new int[numNonbasic];
    rowToVar = new int[maxNumBasic];
    varToTableau = new int[numVars];

    // Initialize maps
    int i;
    for (i = 0; i < numNonbasic; i++) {
      colToVar[i] = i;
      varToTableau[i] = i;
    }
    for (int j = 0; j < maxNumBasic; i++, j++) {
      rowToVar[j] = i;
      varToTableau[i] = j;
    }
  }

  @Override
  public boolean solve() {
    int brokenIdx = 0;
    int suitableIdx = 0;

    while ((brokenIdx = checkBounds()) >= 0) {
      if ((suitableIdx = findSuitable(brokenIdx)) < 0)
        return false;
      pivot(brokenIdx, suitableIdx);
      updateAssignment();
    }
    return true;
  }

  @Override
  public void setBounds(int idx, float lower, float upper) {
    bounds.setBounds(idx, lower, upper);
  }

  @Override
  public void addConstraint(List<Float> cs) {
    if (cs.size() != numColumns)
      throw new RuntimeException("Invalid constraint size.");
    else if (numRows >= maxNumRows)
      throw new RuntimeException("Unable to add more constraints.");
    final int offset = numRows * numColumns;
    for (int i = 0; i < cs.size(); i++) {
      tableau[offset + i] = cs.get(i);
    }
    numRows++;
  }

  @Override
  public List<Float> solution() {
    final ArrayList<Float> s = new ArrayList<>();
    for (int i = 0; i < numColumns; i++)
      s.add(bounds.getAssignment(i));
    return s;
  }

  /**
   * Returns the index of the smallest basic variable whose current assignment violates its bounds
   * (a "broken" variable). The word "smallest" refers to the broken variable that appears earliest
   * in the total-ordering of the variables.
   * 
   * @return The index of a broken variable, or <code>NONE_FOUND</code> if none is found.
   */
  protected int checkBounds() {
    for (int i = 0; i < numVars; i++) {
      if (bounds.isBasic(i) && bounds.isBroken(i))
        return i;
    }
    return NONE_FOUND;
  }

  /**
   * Returns the index of the smallest "suitable" variable, which is a non-basic variable whose
   * assignment may be tweaked to fix a broken variable. If a suitable variable is found, this
   * method will apply the correction by updating the current assignment of both the broken and
   * suitable variables.
   * 
   * <p>
   * The "smallest" suitable variable is the one that occurs earliest in the total-ordering of the
   * variables. A broken variable is fixed when its current assignment no longer violates its
   * bounds.
   * </p>
   * 
   * @param brokenIdx The index of the broken variable.
   * @return If found, the index of a suitable variable; otherwise, returns NONE_FOUND.
   */
  protected abstract int findSuitable(final int brokenIdx);

  /**
   * Performs the "pivot" operation, which swaps the basic and nonbasic variables and, accordingly,
   * updates the tableau.
   * 
   * @param basicIdx The index of the basic variable.
   * @param nonbasicIdx The index of the non-basic variable.
   */
  protected abstract void pivot(final int basicIdx, final int nonbasicIdx);

  /**
   * Updates the assignment of each variable based on the current tableau.
   */
  protected abstract void updateAssignment();

  /**
   * Prints a representation of the backing tableau to the console.
   */
  public void printTableau() {
    System.out.print("     ");
    for (int j = 0; j < numColumns; j++) {
      final int varIdx = colToVar[j];
      final float a = bounds.getAssignment(varIdx);
      if (varIdx >= numColumns)
        System.out.print("[s" + (varIdx - numColumns) + "=" + a + "] ");
      else
        System.out.print("[x" + varIdx + "=" + a + "] ");
    }
    System.out.println();
    for (int i = 0; i < numRows; i++) {
      final int varIdx = rowToVar[i];
      final float a = bounds.getAssignment(varIdx);
      if (varIdx >= numColumns)
        System.out.print("[s" + (varIdx - numColumns) + "=" + a + "] ");
      else
        System.out.print("[x" + varIdx + "=" + a + "] ");
      for (int j = 0; j < numColumns; j++) {
        System.out.print(getTableauEntry(i, j));
        if (j + 1 < numRows)
          System.out.print(" ");
      }
      System.out.println();
    }
  }

  protected abstract float getTableauEntry(int row, int col);

  protected abstract void preSolve();

}
