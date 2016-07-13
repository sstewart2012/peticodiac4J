package ca.uwaterloo.simplex;

import java.util.ArrayList;
import java.util.List;

import ca.uwaterloo.shediac.KernelMgr.DeviceType;
import ca.uwaterloo.simplex.solver.Solver;
import ca.uwaterloo.simplex.solver.SolverProfiler;

public final class Main {

  private static List<Float> makeConstraint(final Float... floats) {
    final List<Float> list = new ArrayList<>();
    for (final Float val : floats)
      list.add(val);
    return list;
  }

  public static void main(String[] args) {
    final int numVars = 2;
    final int numConstraints = 3;
    // final CpuSolver solver = new CpuSolver(numConstraints, numVars);
    // final SolverProfiler solver = new SolverProfiler(new
    // CpuSolver(numConstraints, numVars));
    final SolverProfiler solver =
        new SolverProfiler(Solver.create(numConstraints, numVars, DeviceType.CUDA, 0, 2, true));

    // Add constraints
    solver.addConstraint(makeConstraint(1.0f, 1.0f));
    solver.addConstraint(makeConstraint(2.0f, -1.0f));
    solver.addConstraint(makeConstraint(-1.0f, 2.0f));

    // Set bounds
    solver.setBounds(0, 0, Solver.NO_BOUND);
    solver.setBounds(1, 0, Solver.NO_BOUND);
    solver.setBounds(2, 2, Solver.NO_BOUND);
    solver.setBounds(3, 0, Solver.NO_BOUND);
    solver.setBounds(4, 1, Solver.NO_BOUND);

    // Solve
    float solveTime = -System.nanoTime();
    final boolean result = solver.solve();
    solveTime += System.nanoTime();
    solveTime *= 1e-6f;

    // Print solution (if applicable)
    if (result) {
      System.out.println();
      System.out.println("Solution:");
      final List<Float> solution = solver.solution();
      int i = 0;
      for (final Float x : solution) {
        System.out.printf("  x%d=%.2f\n", i, x);
        i++;
      }
      System.out.println();
    }

    // Print summary
    System.out.printf("Result                : %s\n", result ? "SAT" : "UNSAT");
    System.out.printf("Solving time          : %.3f ms\n", solveTime);

    // solver.printSummary();
  }

}
