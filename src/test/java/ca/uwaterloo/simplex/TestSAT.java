package ca.uwaterloo.simplex;

import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import ca.uwaterloo.simplex.solver.Solver;
import ca.uwaterloo.simplex.solver.SolverProfiler;

public class TestSAT {

  private static List<Float> makeConstraint(final Float... floats) {
    final List<Float> list = new ArrayList<>();
    for (final Float val : floats)
      list.add(val);
    return list;
  }

  @Test
  public void test1() {
    final SolverProfiler solver = new SolverProfiler(Solver.create(3, 2));
    solver.addConstraint(makeConstraint(1.0f, 1.0f));
    solver.addConstraint(makeConstraint(2.0f, -1.0f));
    solver.addConstraint(makeConstraint(-1.0f, 2.0f));
    solver.setBounds(2, 2, Solver.NO_BOUND);
    solver.setBounds(3, 0, Solver.NO_BOUND);
    solver.setBounds(4, 1, Solver.NO_BOUND);
    assertTrue(solver.solve());
    System.out.println("test1");
    solver.printSummary();
    System.out.println();
  }

  @Test
  public void test2() {
    final SolverProfiler solver = new SolverProfiler(Solver.create(2, 3));
    solver.addConstraint(makeConstraint(1.0f, 0.5f, 0.5f));
    solver.addConstraint(makeConstraint(1.5f, 2.0f, 1.0f));
    solver.setBounds(3, 0, 2);
    solver.setBounds(4, 8, Solver.NO_BOUND);
    assertTrue(solver.solve());
    System.out.println("test2");
    solver.printSummary();
    System.out.println();
  }

  @Test
  public void test3() {
    final SolverProfiler solver = new SolverProfiler(Solver.create(3, 3));
    solver.addConstraint(makeConstraint(4.0f, 3.0f, 2.0f));
    solver.addConstraint(makeConstraint(4.0f, 7.0f, 2.0f));
    solver.addConstraint(makeConstraint(9.0f, 6.0f, 2.0f));
    solver.setBounds(3, 7, Solver.NO_BOUND);
    solver.setBounds(4, 3, Solver.NO_BOUND);
    solver.setBounds(5, 10, Solver.NO_BOUND);
    assertTrue(solver.solve());
    System.out.println("test3");
    solver.printSummary();
    System.out.println();
  }
}
