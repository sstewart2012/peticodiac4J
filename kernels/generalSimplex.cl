#define OFFSET(row, col, ncols) (row * ncols + col)
#define NO_BOUND -1
#define EPSILON 0.000001
#define NONBASIC_FLAG 0
#define BASIC_FLAG 1
#define NONE_FOUND -1
#define IS_INCREASABLE(low, upp, ass) (upp == NO_BOUND || ass < upp)
#define IS_DECREASABLE(low, upp, ass) (low == NO_BOUND || ass > low)

__kernel void check_bounds(
	const int n,
	const int offset,
	__global const float* const lower,
	__global const float* const upper,
	__global const float* const assigns,
	__global const unsigned char* const flags,
	__global int* const result
)
{
	const int idx = offset + get_global_id(0);

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
		atomic_min(result, idx);
		//printf("Variable %d is broken (result=%d).\n", idx, *result);
	}
}

/**
 * If found, returns the index of a suitable variable; otherwise, returns
 * NONE_FOUND. The return value is stored in the output argument called
 * suitable_idx.
 */

__kernel void find_suitable(
	const int nvars,
	const int broken_idx,
	const int offset,
	__global const float* const tableau,
	__global const float* const lower,
	__global const float* const upper,
	__global const float* const assigns,
	__global const unsigned char* const flags,
	__global const int* const varToTableau,
	__global int* const suitable_idx
){
	// Determine variable index assigned to this thread
	const int idx = offset + get_global_id(0);

	// Boundary check and "basic" variables are skipped
	if (idx >= nvars || flags[idx] == BASIC_FLAG)
		return;

	// Read bounds information for the broken variable
	const float ass = assigns[broken_idx];
	const float low = lower[broken_idx];
	const float upp = lower[broken_idx];

	// Determine if the broken variable needs to be increased or decreased
	const bool increase = ass < low;

	// Obtain coefficient value in the tableau
	const float coeff = tableau[varToTableau[broken_idx] * nvars + varToTableau[idx]];

	if (increase){
		if ((IS_INCREASABLE(low, upp, ass) && coeff > 0) || (IS_DECREASABLE(low, upp, ass) && coeff < 0)) {
			atomic_min(suitable_idx, idx);
	        //printf("Variable %d is suitable\n", idx, suitable_idx);
		}
	}
	else {
		if ((IS_INCREASABLE(low, upp, ass) && coeff < 0) || (IS_DECREASABLE(low, upp, ass) && coeff > 0)) {
			atomic_min(suitable_idx, idx);
	        //printf("Variable %d is suitable\n", idx, suitable_idx);
		}
	}
}

__kernel void find_suitable_complete(
	const int nvars,
	const int broken_idx,
	const int suitable_idx,
	__global const float* const tableau,
	__global const float* const lower,
	__global const float* const upper,
	__global float* const assigns,
	__global const int* const varToTableau
){
	const int idx = get_global_id(0);

	if (idx > 0)
		return;

	// Read bounds information for the broken variable
	const float ass = assigns[broken_idx];
	const float low = lower[broken_idx];
	const float upp = lower[broken_idx];

	// Determine if the broken variable needs to be increased or decreased
	const bool increase = ass < low;

	// Obtain coefficient value in the tableau
	const float coeff = tableau[varToTableau[broken_idx] * nvars + varToTableau[idx]];

	// Amounts to adjust assignments of suitable and broken variables
	const float delta = increase ? low - ass : ass - upper[broken_idx];
	const float theta = delta / coeff;

	//printf("[%d] brokenIdx=%d suitable_idx=%d coeff=%f delta=%f theta=%f increase=%d tableau[%d]=%f\n", idx, broken_idx, suitable_idx, coeff, delta, theta, increase, idx, tableau[idx]);

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

__kernel void pivot_update_inner(
	const float alpha,
	const int pivot_row,
	const int pivot_col,
	const int nrows,
	const int ncols,
	__global float* const tableau
){
	// Determine thread ID in 2D (x and y)
	const int col = get_global_id(0); // column index
	const int row = get_global_id(1); // row index

	if (col < ncols && row < nrows && row != pivot_row && col != pivot_col) {
		// Compute helpful indices
		const int delta_row_idx = OFFSET(row, 0, ncols);
		const int delta_idx = delta_row_idx + col;

		// Load values from global memory
		const float delta = tableau[delta_idx];
		const float beta = tableau[OFFSET(pivot_row, col, ncols)];
		const float gamma = tableau[delta_row_idx + pivot_col];

		// Store result
		float coeff = delta - (beta * gamma) / alpha;
		tableau[delta_idx] = coeff;
	}
}

__kernel void pivot_update_row(
	const float alpha,
	const int row,
	const int ncols,
	__global float* const tableau
){
	__global float* const tableau_row = &tableau[row * ncols];
	const int col = get_global_id(0);
	if (col >= ncols)
		return;
	const float beta = tableau_row[col];
	const float coeff = -beta / alpha;
	tableau_row[col] = coeff;
}

__kernel void pivot_update_column(
	const float alpha,
	const int col,
	const int nrows,
	const int ncols,
	__global float* const tableau
){
	__global float* const tableau_col = tableau + col;
	const int row = get_global_id(0);
	if (row >= nrows)
		return;
	const int idx = row * ncols;
	const float gamma = tableau_col[idx];
	tableau_col[idx] = gamma / alpha;
}

__kernel void update_assignment(
	__global const float* const input,
	__global const float* const assigns,
	__global const int* const colToVar,
	__global float* const output,
	__local float* const partial_sums
){
	const int tid = get_global_id(0);
	const int idx = get_local_id(0);

	// Pre-fetch and multiply by corresponding assignment
	const float a = assigns[colToVar[tid]];
	partial_sums[idx] = a * input[tid];
	barrier(CLK_LOCAL_MEM_FENCE);

	// Reduce using interleaved pairs
	for (int stride = get_local_size(0) / 2; stride >= 0; stride >>= 1) {
		if (idx < stride) {
			partial_sums[idx] += partial_sums[idx + stride];
		}
		barrier(CLK_LOCAL_MEM_FENCE);
	}

	// Write the result for this block to global memory
	if (idx == 0) {
		output[get_group_id(0)] = partial_sums[0];
	}
}

__kernel void update_assignment_complete(
	const int var,
	__global const float* const input,
	__global float* const assigns,
	__local float* const partial_sums
){
	const int idx = get_local_id(0);

	// Pre-fetch
	partial_sums[idx] = input[idx];
	barrier(CLK_LOCAL_MEM_FENCE);

	// Reduce
	for (int stride = get_local_size(0) / 2; stride >= 0; stride >>= 1) {
		if (idx < stride) {
			partial_sums[idx] += partial_sums[idx + stride];
		}
		barrier(CLK_LOCAL_MEM_FENCE);
	}

	// Write the result to the assignments array
	if (idx == 0) {
		assigns[var] = partial_sums[0];
		printf("[%d] a(%d)=%f\n", idx, var, assigns[var]);
	}
}
