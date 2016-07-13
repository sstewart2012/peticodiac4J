package ca.uwaterloo.simplex.bounds;

/**
 * The public interface for a Bounds object. Each variable in the input to the solver has a lower
 * bound, an upper bound, an assignment. The assignment can change during the course of solving, but
 * the bounds cannot. Each variable also has a flag, which indicates whether or not it is "basic" or
 * "nonbasic."
 * 
 * @author Steven Stewart
 */
public interface Bounds {

  /** Constant that indicates a basic variable. */
  public static byte BASIC = 1;

  /** Constant that indicates a nonbasic variable. */
  public static byte NON_BASIC = 0;

  /** Returns a reference to the lower bounds. */
  float[] getLower();

  /** Returns a reference to the upper bounds. */
  float[] getUpper();

  /** Returns a reference to the variable assignments. */
  float[] getAssignments();

  /** Returns a reference to the flags, which indicate which variables are basic or nonbasic. */
  byte[] getFlags();

  /** Returns <code>true</code> if variable <code>i</code> is basic. */
  boolean isBasic(final int i);

  /** Returns <code>true</code> if variable <code>i</code> is nonbasic. */
  boolean isNonBasic(final int i);

  /** Sets the lower and upper bounds of variable <code>i</code>. */
  default void setBounds(final int i, final float lower, final float upper) {
    setLowerBound(i, lower);
    setUpperBound(i, upper);
  }

  /** Sets the lower bound of variable <code>i</code> to the specified value <code>val</code>. */
  void setLowerBound(final int i, final float val);

  /** Returns the lower bound of variable <code>i</code>. */
  float getLowerBound(final int i);

  /** Sets the upper bound of variable <code>i</code> to the specified value <code>val</code>. */
  void setUpperBound(final int i, final float val);

  /** Returns the upper bound of variable <code>i</code>. */
  float getUpperBound(final int i);

  /** Sets the assignment of variable <code>i</code> to the specified value <code>val</code>. */
  void setAssignment(final int i, final float val);

  /** Returns the current assignment of variable <code>i</code>. */
  float getAssignment(final int i);

  /** Returns true if the current assignment of variable <code>i</code> violates its bounds. */
  boolean isBroken(final int i);

  /**
   * Returns true if there is room within its bounds for variable <code>i</code> to be increased.
   */
  boolean isIncreasable(final int i);

  /**
   * Returns true if there is room within its bounds for variable <code>i</code> to be decreased.
   */
  boolean isDecreasable(final int i);

  /** Returns the number of basic variables. */
  int numBasic();

  /** Returns the number of nonbasic variables. */
  int numNonbasic();

  /** Returns the number of variables: <code>numBasic() + numNonbasic()</code>. */
  default int numVars() {
    return numBasic() + numNonbasic();
  }

  /** Increases the assignment of variable <code>i</code> by the value <code>f</code>. */
  void increaseAssignment(final int i, final float f);

  /** Decreases the assignment of variable <code>i</code> by the value <code>f</code>. */
  void decreaseAssignment(final int i, final float f);

  /** Sets the flag property of variable <code>i</code>. */
  void setFlag(int i, byte val);

}
