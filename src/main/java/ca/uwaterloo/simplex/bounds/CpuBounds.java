package ca.uwaterloo.simplex.bounds;

public class CpuBounds implements Bounds {

  protected final int basic;
  protected final int nonbasic;
  protected final int numVars;
  protected final float[] lower;
  protected final float[] upper;
  protected final float[] assigns;
  protected final byte[] flags;
  protected final float EPSILON;
  protected final float NO_BOUND = -1;

  public CpuBounds(final int basic, final int nonbasic, final float epsilon) {
    this.basic = basic;
    this.nonbasic = nonbasic;
    this.numVars = basic + nonbasic;
    this.EPSILON = epsilon;

    lower = new float[numVars];
    upper = new float[numVars];
    assigns = new float[numVars];
    flags = new byte[numVars];

    int i;
    for (i = 0; i < nonbasic; i++) {
      lower[i] = 0.0f;
      upper[i] = NO_BOUND;
      flags[i] = Bounds.NON_BASIC;
    }
    for (int j = 0; j < basic; j++, i++) {
      lower[i] = 0.0f;
      upper[i] = NO_BOUND;
      flags[i] = Bounds.BASIC;
    }
  }

  @Override
  public float[] getLower() {
    return lower;
  }

  @Override
  public float[] getUpper() {
    return upper;
  }

  @Override
  public float[] getAssignments() {
    return assigns;
  }

  @Override
  public byte[] getFlags() {
    return flags;
  }
  
  @Override
  public void setFlag(final int i, final byte val) {
    flags[i] = val;
  }

  @Override
  public boolean isBasic(final int i) {
    return flags[i] == Bounds.BASIC;
  }

  @Override
  public boolean isNonBasic(final int i) {
    return flags[i] == Bounds.NON_BASIC;
  }

  @Override
  public void setBounds(final int i, final float lower, final float upper) {
    setLowerBound(i, lower);
    setUpperBound(i, upper);
  }

  @Override
  public void setLowerBound(final int i, final float val) {
    lower[i] = val;
  }

  @Override
  public float getLowerBound(final int i) {
    return lower[i];
  }

  @Override
  public void setUpperBound(final int i, final float val) {
    upper[i] = val;
  }

  @Override
  public float getUpperBound(final int i) {
    return upper[i];
  }

  @Override
  public void setAssignment(final int i, final float val) {
    assigns[i] = val;
  }

  @Override
  public float getAssignment(final int i) {
    return assigns[i];
  }

  @Override
  public boolean isBroken(final int i) {
    assert isBasic(i);
    final float assign = this.assigns[i];
    final float low = this.lower[i];
    final float upp = this.upper[i];
    if (Math.abs(assign - low) < EPSILON) {
      return false;
    } else if (Math.abs(assign - upp) < EPSILON) {
      return false;
    } else if (low != NO_BOUND && assign < low) {
      return true;
    } else if (upp != NO_BOUND && assign > upp) {
      return true;
    } else {
      return false;
    }
  }

  @Override
  public boolean isIncreasable(final int idx) {
    return upper[idx] == NO_BOUND || assigns[idx] < upper[idx];
  }

  @Override
  public boolean isDecreasable(final int idx) {
    return lower[idx] == NO_BOUND || assigns[idx] > lower[idx];
  }

  @Override
  public int numBasic() {
    return basic;
  }

  @Override
  public int numNonbasic() {
    return nonbasic;
  }

  @Override
  public int numVars() {
    return numVars;
  }

  @Override
  public void increaseAssignment(final int i, final float f) {
    assigns[i] += f;
  }

  @Override
  public void decreaseAssignment(final int i, final float f) {
    assigns[i] -= f;
  }

}
