#define OFFSET(row, col, ncols) (row * ncols + col)
#define NO_BOUND -1
#define EPSILON 0.000001
#define NONBASIC_FLAG 0
#define BASIC_FLAG 1

extern "C"
__global__ void check_bounds(
	const int n,
	const int offset,
	const float* const lower,
	const float* const upper,
	const float* const assigns,
	const unsigned char* const flags,
	int* const result
)
{
	const int idx = offset + blockIdx.x * blockDim.x + threadIdx.x;

	// Boundary check and nonbasic variables are skipped
	if (idx >= n || flags[idx] == NONBASIC_FLAG)
		return;

	//printf("[%d] n=%d offset=%d flags=%d\n", idx, n, offset, flags[idx]); return;

	//printf("[%d] lowPtr=%p uppPtr=%p offset=%d n=%d flagPtr=%p\n", idx, lower, upper, *offset, n, flags);
	const float ass = assigns[idx];
	const float low = lower[idx];
	const float upp = upper[idx];
	//printf("[%d] low=%f ass=%f upp=%f\n", idx, low, ass, upp);

	const bool testA = fabsf(ass - low) < EPSILON;
	const bool testB = fabsf(ass - upp) < EPSILON;
	const bool testC = low != NO_BOUND && ass < low;
	const bool testD = upp != NO_BOUND && ass > upp;

	if (testA || testB || !(testC || testD)) {
		return;
	} else {
		atomicMin(result, idx);
		//printf("Variable %d is broken (result=%d).\n", idx, *result);
	}
}

#define NONE_FOUND -1
#define IS_INCREASABLE(low, upp, ass) (upp == NO_BOUND || ass < upp)
#define IS_DECREASABLE(low, upp, ass) (low == NO_BOUND || ass > low)

/**
 * If found, returns the index of a suitable variable; otherwise, returns
 * NONE_FOUND. The return value is stored in the output argument called
 * suitable_idx.
 */
 extern "C"
__global__ void find_suitable(
	const int ncols,
	const int broken_idx,
	const int offset,
	const float* const tableau,
	const float* const lower,
	const float* const upper,
	const float* const assigns,
	const unsigned char* const flags,
	const int* const varToTableau,
	const int* const colToVar,
	int* const suitable_idx
){
	// Determine variable index assigned to this thread
	const int idx = offset + (blockIdx.x * blockDim.x + threadIdx.x);
	const int var = colToVar[idx];

	// Boundary check and "basic" variables are skipped
	if (idx >= ncols || flags[var] == BASIC_FLAG)
		return;

	// Determine if the broken variable needs to be increased or decreased
	const bool increase = assigns[broken_idx] < lower[broken_idx];

	// Read bounds information needed to determine if potential suitable variable
	// is increaseable or decreaseable
	const float ass = assigns[var];
	const float low = lower[var];
	const float upp = upper[var];

	// Obtain coefficient value in the tableau
	const float coeff = tableau[varToTableau[broken_idx] * ncols + varToTableau[var]];

	//printf("[%d] offset=%d ncols=%d low=%f ass=%f upp=%f increase=%d coeff=%f\n",
	//	idx, offset, ncols, low, ass, upp, increase, coeff);
	
	if (increase){
		if ((IS_INCREASABLE(low, upp, ass) && coeff > 0) 
				|| (IS_DECREASABLE(low, upp, ass) && coeff < 0)) {
	        atomicMin(suitable_idx, var);
	        //printf("Variable %d is suitable\n", idx, suitable_idx);
		}
	}
	else {
		if ((IS_INCREASABLE(low, upp, ass) && coeff < 0) 
				|| (IS_DECREASABLE(low, upp, ass) && coeff > 0)) {
	        atomicMin(suitable_idx, var);
	        //printf("Variable %d is suitable\n", idx, suitable_idx);
		}
	}
}

extern "C"
__global__ void find_suitable_complete(
	const int ncols,
	const int broken_idx,
	const int suitable_idx,
	const float* const tableau,
	const float* const lower,
	const float* const upper,
	float* const assigns,
	const int* const varToTableau
){
	if (blockIdx.x * blockDim.x + threadIdx.x > 0)
		return;

	// Read bounds information for the broken variable
	float ass = assigns[broken_idx];
	float low = lower[broken_idx];
	float upp = upper[broken_idx];

	// Determine if the broken variable needs to be increased or decreased
	const bool increase = ass < low;

	// Obtain coefficient value in the tableau
	const float coeff = tableau[varToTableau[broken_idx] * ncols
		+ varToTableau[suitable_idx]];

	// Amounts to adjust assignments of suitable and broken variables
	const float delta = increase ? low - ass : ass - upp;
	const float theta = delta / coeff;

	//printf("[%d] b=%d s=%d increase=%d delta=%f theta=%f\n",
	//	threadIdx.x, broken_idx, suitable_idx, increase, delta, theta);

	// Read bounds info for the suitable variable to check if
	// increaseable or decreaseable
	ass = assigns[suitable_idx];
	low = lower[suitable_idx];
	upp = upper[suitable_idx];

	if (increase) {
		if ((IS_INCREASABLE(low, upp, ass) && coeff > 0) ||
				(IS_DECREASABLE(low, upp, ass) && coeff < 0)) {
			assigns[suitable_idx] += coeff < 0 ? -theta : theta;
			assigns[broken_idx] += delta;
			//printf("a(%d) = %f\n", broken_idx, assigns[broken_idx]);
			//printf("a(%d) = %f\n", suitable_idx, assigns[suitable_idx]);
		}
	}
	else {
		if ((IS_INCREASABLE(low, upp, ass) && coeff < 0) ||
				(IS_DECREASABLE(low, upp, ass) && coeff > 0)) {
			assigns[suitable_idx] -= coeff < 0 ? theta : -theta;
			assigns[broken_idx] -= delta;
			//printf("a(%d) = %f\n", broken_idx, assigns[broken_idx]);
			//printf("a(%d) = %f\n", suitable_idx, assigns[suitable_idx]);
		}
	}
}

extern "C"
__global__ void pivot_update_inner(
	const float alpha,
	const int pivot_row,
	const int pivot_col,
	const int nrows,
	const int ncols,
	float* const tableau
){
	// Determine thread ID in 2D (x and y)
	const unsigned int col = blockDim.x * blockIdx.x + threadIdx.x; // column index
	const unsigned int row = blockDim.y * blockIdx.y + threadIdx.y; // row index

	if (col < ncols && row < nrows && row != pivot_row && col != pivot_col) {
		// Compute helpful indices
		const unsigned int delta_row_idx = OFFSET(row, 0, ncols);
		const unsigned int delta_idx = delta_row_idx + col;

		// Load values from global memory
		const float delta = tableau[delta_idx];
		const float beta = tableau[OFFSET(pivot_row, col, ncols)];
		const float gamma = tableau[delta_row_idx + pivot_col];

		// Store result
		float coeff = delta - (beta * gamma) / alpha;
		tableau[delta_idx] = coeff;
	}
}

extern "C"
__global__ void pivot_update_row(
	const float alpha,
	const int row,
	const int ncols,
	float* const tableau
){
	float* const tableau_row = &tableau[row * ncols];
	const int col = blockIdx.x * blockDim.x + threadIdx.x;
	if (col >= ncols)
		return;
	const float beta = tableau_row[col];
	const float coeff = -beta / alpha;
	tableau_row[col] = coeff;
}

extern "C"
__global__ void pivot_update_column(
	const float alpha,
	const int col,
	const int nrows,
	const int ncols,
	float* const tableau
){
	float* const tableau_col = tableau + col;
	const int row = blockIdx.x * blockDim.x + threadIdx.x;
	if (row >= nrows)
		return;
	const int idx = row * ncols;
	const float gamma = tableau_col[idx];
	tableau_col[idx] = gamma / alpha;
}

extern "C"
__global__ void update_assignment(
	const int n,
	const float* const input,
	const float* const assigns,
	const int* const colToVar,
	float* const output
){
	extern __shared__ float partial_sums[];
	const int gid = blockDim.x * blockIdx.x + threadIdx.x;
	const int lid = threadIdx.x;
	
	// Boundary check
	if (gid >= n)
		return;

	//printf("[%d] n=%d\n", gid, n); return;

	// Pre-fetch and multiply by corresponding assignment
	const float a = assigns[colToVar[gid]];
	partial_sums[lid] = a * input[gid];
	__syncthreads();

	//printf("[%d] n=%d psum=%f\n", gid, n, partial_sums[lid]);
	//return;

	// Reduce using interleaved pairs
	for (int stride = blockDim.x / 2; stride > 0; stride >>= 1) {
		if (lid < stride) {
			partial_sums[lid] += partial_sums[lid + stride];
		}
		__syncthreads();
	}

	// Write the result for this block to global memory
	if (lid == 0) {
		output[blockIdx.x] = partial_sums[0];
	}
}

extern "C"
__global__ void update_assignment_complete(
	const int var,
	const float* const input,
	float* const assigns
){
	extern __shared__ float partial_sums[];
	const int lid = threadIdx.x;
	//printf("[%d] var=%d input=%f\n", lid, var, input[idx], input);

	// Pre-fetch
	partial_sums[lid] = input[lid];
	__syncthreads();
	//printf("[%d] offset=%d var=%d partial_sums=%f\n", lid, offset, var, partial_sums[idx]);

	// Reduce
	for (int stride = blockDim.x / 2; stride > 0; stride >>= 1) {
		if (lid < stride) {
			partial_sums[lid] += partial_sums[lid + stride];
		}
		__syncthreads();
	}

	// Write the result to the assignments array
	if (lid == 0) {
		assigns[var] = partial_sums[0];
		//printf("[%d] a(%d)=%f\n", lid, var, assigns[var]);
	}
}