package ca.uwaterloo.simplex;

import static org.junit.Assert.assertFalse;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import ca.uwaterloo.shediac.KernelMgr.DeviceType;
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

  private void test1(final Solver solver) {
    solver.addConstraint(makeConstraint(1.0f, 0.0f));
    solver.addConstraint(makeConstraint(0.0f, 1.0f));
    solver.addConstraint(makeConstraint(1.0f, 1.0f));
    solver.setBounds(2, 6, CpuSolver.NO_BOUND);
    solver.setBounds(3, 6, CpuSolver.NO_BOUND);
    solver.setBounds(4, 0, 11);
    assertFalse(solver.solve());
  }
  
  @Test
  public void test1_cpu() {
    test1(new SolverProfiler(Solver.create(3, 2)));
  }
  
  @Test
  public void test1_cuda() {
    test1(new SolverProfiler(Solver.create(3, 2, DeviceType.CUDA, 0, 0, true)));
  }

}
