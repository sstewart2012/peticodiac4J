package ca.uwaterloo.simplex.solver;

import java.util.SortedSet;
import java.util.TreeSet;

import ca.uwaterloo.simplex.bounds.Bounds;

/**
 * A CPU-only implementation of a linear constraint solver.
 * 
 * @author Steven Stewart
 */
public class CpuSolver extends AbstractSolver {

  /** The set of indices of basic variables. */
  private final SortedSet<Integer> basic = new TreeSet<>();

  /** The set of indices of non-basic variables. */
  private final SortedSet<Integer> nonbasic = new TreeSet<>();

  /**
   * 
   * @param numVars
   * @param maxNumConstrs
   */
  CpuSolver(final int maxNumBasic, final int numNonbasic) {
    super(maxNumBasic, numNonbasic, BoundsType.CPU);

    // Initialize maps
    int i;
    for (i = 0; i < numNonbasic; i++)
      nonbasic.add(i);
    for (int j = 0; j < maxNumBasic; i++, j++)
      basic.add(i);
  }

  @Override
  protected int checkBounds() {
    for (final Integer i : basic)
      if (bounds.isBroken(i))
        return i;
    return NONE_FOUND;
  }

  private float lookup(final int row, final int col) {
    final int rowIdx = varToTableau[row];
    final int colIdx = varToTableau[col];
    return tableau[rowIdx * numColumns + colIdx];
  }

  @Override
  protected int findSuitable(final int brokenIdx) {
    final boolean increase = bounds.getAssignment(brokenIdx) < bounds.getLowerBound(brokenIdx);
    final float delta = increase ? bounds.getLowerBound(brokenIdx) - bounds.getAssignment(brokenIdx)
        : bounds.getAssignment(brokenIdx) - bounds.getUpperBound(brokenIdx);
    if (increase)
      return findSuitableIncrease(brokenIdx, delta);
    else
      return findSuitableDecrease(brokenIdx, delta);
  }

  protected int findSuitableIncrease(final int brokenIdx, final float delta) {
    for (final Integer idx : nonbasic) {
      final float coeff = lookup(brokenIdx, idx);
      if ((bounds.isIncreasable(idx) && coeff > 0) || (bounds.isDecreasable(idx) && coeff < 0)) {
        final float theta = delta / coeff;
        bounds.increaseAssignment(idx, coeff < 0 ? -theta : theta);
        bounds.increaseAssignment(brokenIdx, delta);
        return idx;
      }
    }
    return NONE_FOUND;
  }

  protected int findSuitableDecrease(final int brokenIdx, final float delta) {
    for (final Integer idx : nonbasic) {
      final float coeff = lookup(brokenIdx, idx);
      if ((bounds.isIncreasable(idx) && coeff < 0) || (bounds.isDecreasable(idx) && coeff > 0)) {
        final float theta = delta / coeff;
        bounds.decreaseAssignment(idx, coeff < 0 ? theta : -theta);
        bounds.decreaseAssignment(brokenIdx, delta);
        return idx;
      }
    }
    return NONE_FOUND;
  }

  @Override
  protected void pivot(final int basicIdx, final int nonbasicIdx) {
    assert basicIdx >= 0 && basicIdx < numVars;
    assert nonbasicIdx >= 0 && nonbasicIdx < numVars;

    // Get the actual row and column indices
    final int row = varToTableau[basicIdx];
    final int col = varToTableau[nonbasicIdx];

    // Save current value of alpha
    final int alphaIdx = row * numColumns + col;
    final float alpha = tableau[alphaIdx];

    // Update the tableau
    updateInner(alpha, row, col);
    updatePivotRow(alpha, row);
    updatePivotCol(alpha, col);
    tableau[alphaIdx] = 1.0f / alpha;

    // Swap the basic and non-basic variables
    swap(basicIdx, nonbasicIdx);
  }

  private void swap(final int basicVar, final int nonbasicVar) {
    final int basicTableauIdx = varToTableau[basicVar];
    final int nonbasicTableauIdx = varToTableau[nonbasicVar];

    // Swap basic and non-basic variables
    basic.remove(basicVar);
    nonbasic.remove(nonbasicVar);
    basic.add(nonbasicVar);
    nonbasic.add(basicVar);
    bounds.setFlag(basicVar, Bounds.NON_BASIC);
    bounds.setFlag(nonbasicVar, Bounds.BASIC);

    // Update tableau row/col to variable index mappings
    rowToVar[basicTableauIdx] = nonbasicVar;
    colToVar[nonbasicTableauIdx] = basicVar;

    // Update tableau index for variables
    varToTableau[basicVar] = nonbasicTableauIdx;
    varToTableau[nonbasicVar] = basicTableauIdx;
  }

  private void updateInner(final float alpha, final int row, final int col) {
    for (int i = 0; i < numRows; i++) {
      if (i == row)
        continue;
      for (int j = 0; j < numColumns; j++) {
        if (j == col)
          continue;
        final int deltaRowIdx = i * numColumns;
        final int deltaIdx = deltaRowIdx + j;
        final float delta = tableau[deltaIdx];
        final float beta = tableau[row * numColumns + j];
        final float gamma = tableau[deltaRowIdx + col];
        tableau[deltaIdx] = delta - (beta * gamma) / alpha;
      }
    }
  }

  private void updatePivotRow(final float alpha, final int row) {
    for (int i = 0, idx = row * numColumns; i < numColumns; i++, idx++) {
      tableau[idx] = -tableau[idx] / alpha;
    }
  }

  private void updatePivotCol(final float alpha, final int col) {
    for (int i = 0, idx = col; i < numRows; i++, idx += numColumns) {
      tableau[idx] = tableau[idx] / alpha;
    }
  }

  @Override
  protected void updateAssignment() {
    for (int i = 0; i < numRows; i++) {
      float accum = 0.0f;
      final int offset = i * numColumns;
      for (int j = 0; j < numColumns; j++) {
        accum += bounds.getAssignment(colToVar[j]) * tableau[offset + j];
      }
      bounds.setAssignment(rowToVar[i], accum);
    }
  }

  @Override
  protected void preSolve() {}

  @Override
  protected float getTableauEntry(final int row, final int col) {
    return tableau[row * numColumns + col];
  }

}
