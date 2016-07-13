package ca.uwaterloo.simplex.solver;

import java.util.List;

import ca.uwaterloo.shediac.KernelMgr.DeviceType;


/**
 * The public API for a Solver.
 * 
 * @author Steven Stewart
 */
public interface Solver {

  final int NONE_FOUND = -1;
  final static float EPSILON = 0.000001f;
  final static int NO_BOUND = -1;

  static AbstractSolver create(final int maxNumBasic, final int numNonbasic) {
    return new CpuSolver(maxNumBasic, numNonbasic);
  }
  
  static AbstractSolver create(final int maxNumBasic, final int numNonbasic, final DeviceType type, final int platformId, final int deviceId, final boolean enableExceptions) {
    switch (type) {
      case CUDA: {
        return new DeviceSolver(maxNumBasic, numNonbasic, type, platformId, deviceId, enableExceptions);
      }
      case OpenCL: {
        return new DeviceSolver(maxNumBasic, numNonbasic, type, platformId, deviceId, enableExceptions);
      }
      default:
        throw new RuntimeException("Unsupported device type.");
    }
  }

  /**
   * Adds a linear constraint to the formula to be solved.
   * 
   * <p>
   * A <code>RuntimeException</code> will be thrown if the length of the constraint is not equal to
   * the number of variables in the formula, or if the maximum number of constraints that can be
   * added to the tableau has already been reached.
   * </p>
   * 
   * @param cs A list of values of the linear constraint.
   * @throws RuntimeException If the tableau is full or <code>cs</code> has the wrong number of
   *         entries.
   */
  void addConstraint(List<Float> cs);

  /**
   * Sets the lower and upper bounds of the variable of the specified index.
   * 
   * @param idx The index of the variable.
   * @param lower The lower bound.
   * @param upper The upper bound.
   */
  void setBounds(int idx, float lower, float upper);

  /**
   * If the solver has found a solution, this method returns it.
   * 
   * @return The solution.
   */
  List<Float> solution();

  /**
   * Runs the solving procedure.
   * 
   * @return <code>true</code> if the solver finds a solution, or <code>false</code> if the solver
   *         does not find a solution
   */
  boolean solve();

}
