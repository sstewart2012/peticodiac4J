package ca.uwaterloo.simplex;

import static org.junit.Assert.assertFalse;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import ca.uwaterloo.simplex.solver.CpuSolver;
import ca.uwaterloo.simplex.solver.Solver;
import ca.uwaterloo.simplex.solver.SolverProfiler;

public class TestUNSAT {

  private static List<Float> makeConstraint(final Float... floats) {
    final List<Float> list = new ArrayList<>();
    for (final Float val : floats)
      list.add(val);
    return list;
  }

  @Test
  public void test1() {
    final SolverProfiler solver = new SolverProfiler(Solver.create(3, 2));
    solver.addConstraint(makeConstraint(1.0f, 0.0f));
    solver.addConstraint(makeConstraint(0.0f, 1.0f));
    solver.addConstraint(makeConstraint(1.0f, 1.0f));
    solver.setBounds(2, 6, CpuSolver.NO_BOUND);
    solver.setBounds(3, 6, CpuSolver.NO_BOUND);
    solver.setBounds(4, 0, 11);
    assertFalse(solver.solve());
  }

}
