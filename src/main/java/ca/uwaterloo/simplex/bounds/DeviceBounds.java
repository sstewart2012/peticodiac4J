package ca.uwaterloo.simplex.bounds;

import ca.uwaterloo.shediac.memory.Memory;

public class DeviceBounds implements Bounds {

  private final int numBasic;
  private final int numNonbasic;
  private final int numVars;
  //private final float epsilon;

  public Memory memLower;
  public Memory memUpper;
  public Memory memAssigns;
  public Memory memFlags;

  public DeviceBounds(final int numBasic, final int numNonbasic, final float epsilon) {
    this.numBasic = numBasic;
    this.numNonbasic = numNonbasic;
    //this.epsilon = epsilon;
    numVars = numBasic + numNonbasic;
  }

  @Override
  public float[] getLower() {
    float[] data = new float[numVars];
    for (int i = 0; i < numVars; i++)
      data[i] = memLower.asFloatMemory().get(0);
    return data;
  }

  @Override
  public float[] getUpper() {
    float[] data = new float[numVars];
    for (int i = 0; i < numVars; i++)
      data[i] = memUpper.asFloatMemory().get(0);
    return data;
  }

  @Override
  public float[] getAssignments() {
    float[] data = new float[numVars];
    for (int i = 0; i < numVars; i++)
      data[i] = memAssigns.asFloatMemory().get(0);
    return data;
  }

  @Override
  public byte[] getFlags() {
    byte[] data = new byte[numVars];
    for (int i = 0; i < numVars; i++)
      data[i] = memFlags.asByteMemory().get(0);
    return data;
  }

  @Override
  public boolean isBasic(int i) {
    return memFlags.asByteMemory().get(i) == BASIC;
  }

  @Override
  public boolean isNonBasic(int i) {
    return memFlags.asByteMemory().get(i) == NON_BASIC;
  }

  @Override
  public void setLowerBound(int i, float val) {
    memLower.asFloatMemory().set(i, val);
  }

  @Override
  public float getLowerBound(int i) {
    return memLower.asFloatMemory().get(i);
  }

  @Override
  public void setUpperBound(int i, float val) {
    memUpper.asFloatMemory().set(i, val);
  }

  @Override
  public float getUpperBound(int i) {
    return memUpper.asFloatMemory().get(i);
  }

  @Override
  public void setAssignment(int i, float val) {
    memAssigns.asFloatMemory().set(i, val);
  }

  @Override
  public float getAssignment(int i) {
    return memAssigns.asFloatMemory().get(i);
  }

  @Override
  public boolean isBroken(int i) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean isIncreasable(int idx) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean isDecreasable(int idx) {
    throw new UnsupportedOperationException();
  }

  @Override
  public int numBasic() {
    return numBasic;
  }

  @Override
  public int numNonbasic() {
    return numNonbasic;
  }

  @Override
  public int numVars() {
    return numVars;
  }

  @Override
  public void increaseAssignment(int i, float f) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void decreaseAssignment(int i, float f) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void setFlag(int i, byte val) {
    memFlags.asByteMemory().set(i, val);    
  }


}
