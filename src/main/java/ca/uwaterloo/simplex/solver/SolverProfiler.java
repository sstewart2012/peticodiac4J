package ca.uwaterloo.simplex.solver;

import java.util.List;

/**
 * This class provides a wrapper around an <code>AbstractSolver</code> for the purpose of profiling
 * the methods called during the solving procedure.
 * 
 * @author Steven Stewart
 */
public final class SolverProfiler implements Solver {

  private final AbstractSolver solver;

  public long steps = 0;
  public double timeCheckBounds = 0.0;
  public double timeFindSuitable = 0.0;
  public double timePivot = 0.0;
  public double timeUpdateAssignment = 0.0;
  public double timeSolve = 0.0;

  /**
   * Instantiates the profiler with a solver instance.
   */
  public SolverProfiler(final AbstractSolver solver) {
    this.solver = solver;
  }

  /**
   * Prints a profiling summary of the methods used by the solving procedure.
   */
  public void printSummary() {
    System.out.printf("Steps                  : %d\n", steps);
    System.out.printf("checkBounds()          : %.3f ms (%.2f)\n", timeCheckBounds,
        (timeCheckBounds / timeSolve) * 100.0);
    System.out.printf("findSuitable()         : %.3f ms (%.2f)\n", timeFindSuitable,
        (timeFindSuitable / timeSolve) * 100.0);
    System.out.printf("pivot()                : %.3f ms (%.2f)\n", timePivot,
        (timePivot / timeSolve) * 100.0);
    System.out.printf("updateAssignment()     : %.3f ms (%.2f)\n", timeUpdateAssignment,
        (timeUpdateAssignment / timeSolve) * 100.0);
    System.out.printf("solve()                : %.3f ms\n", timeSolve);
  }

  @Override
  public boolean solve() {
    timeSolve = -System.nanoTime();
    boolean val = solveProcedure();
    timeSolve += System.nanoTime();
    timeSolve *= 1.0e-6;
    return val;
  }

  /**
   * An implementation of the solving procedure.
   * 
   * @return <code>true</code> if the solver finds a solution, or <code>false</code> if the solver
   *         does not find a solution
   */
  private boolean solveProcedure() {
    solver.preSolve();
    printTableau();
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
  public void addConstraint(List<Float> cs) {
    solver.addConstraint(cs);
  }

  @Override
  public void setBounds(int idx, float lower, float upper) {
    solver.setBounds(idx, lower, upper);
  }

  @Override
  public List<Float> solution() {
    return solver.solution();
  }

  protected int checkBounds() {
    double time = -System.nanoTime();
    final int val = solver.checkBounds();
    time += System.nanoTime();
    timeCheckBounds += time * 1.0e-6;
    return val;
  }

  protected int findSuitable(int brokenIdx) {
    double time = -System.nanoTime();
    final int val = solver.findSuitable(brokenIdx);
    time += System.nanoTime();
    timeFindSuitable += time * 1.0e-6;
    return val;
  }

  protected void pivot(int pivotRow, int pivotCol) {
    steps++;
    double time = -System.nanoTime();
    solver.pivot(pivotRow, pivotCol);
    time += System.nanoTime();
    timePivot += time * 1.0e-6;
  }

  protected void updateAssignment() {
    double time = -System.nanoTime();
    solver.updateAssignment();;
    time += System.nanoTime();
    timeUpdateAssignment += time * 1.0e-6;
  }

  public void printTableau() {
    solver.printTableau();
  }

}
