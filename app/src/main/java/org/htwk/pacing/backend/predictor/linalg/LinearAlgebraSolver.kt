package org.htwk.pacing.backend.predictor.linalg

import org.jetbrains.kotlinx.multik.api.identity
import org.jetbrains.kotlinx.multik.api.linalg.dot
import org.jetbrains.kotlinx.multik.api.linalg.solve
import org.jetbrains.kotlinx.multik.api.mk
import org.jetbrains.kotlinx.multik.api.zeros
import org.jetbrains.kotlinx.multik.ndarray.data.D1Array
import org.jetbrains.kotlinx.multik.ndarray.data.D2Array
import org.jetbrains.kotlinx.multik.ndarray.data.get
import org.jetbrains.kotlinx.multik.ndarray.data.set
import org.jetbrains.kotlinx.multik.ndarray.operations.plus
import org.jetbrains.kotlinx.multik.ndarray.operations.times

object LinearAlgebraSolver {
    /**
     * Extracts a column vector from a 2D matrix as a `DoubleArray`.
     *
     * @param matrix The input matrix from which to extract the column.
     * @param colIndex The index of the column to extract.
     * @return A `DoubleArray` representing the specified column.
     */
    private fun extractColumnVector(matrix: D2Array<Double>, colIndex: Int): DoubleArray =
        DoubleArray(matrix.shape[0]) { row -> matrix[row, colIndex] }

    /**
     * Computes the dot product of two vectors.
     *
     * @param a The first vector.
     * @param b The second vector.
     * @return The scalar dot product of the two vectors.
     * @throws IllegalArgumentException if the vectors are not of equal length.
     */
    private fun dotProduct(a: DoubleArray, b: DoubleArray): Double =
        a.zip(b).sumOf { (x, y) -> x * y }

    /**
     * Subtracts the projection of a vector onto a basis vector.
     *
     * This operation computes: `vector - coeff * basis`, element-wise.
     *
     * @param vector The original vector to be adjusted.
     * @param basis The basis vector onto which the projection was made.
     * @param coeff The scalar projection coefficient.
     * @return A new vector with the projection subtracted.
     */
    private fun subtractProjection(
        vector: DoubleArray,
        basis: DoubleArray,
        coeff: Double
    ): DoubleArray =
        vector.zip(basis).map { (v, b) -> v - coeff * b }.toDoubleArray()

    /**
     * Normalizes a vector by dividing each element by its norm.
     *
     * @param vector The vector to normalize.
     * @param norm The norm (magnitude) of the vector.
     * @return A new vector with unit length.
     * @throws IllegalArgumentException if norm is zero or near-zero.
     */
    private fun normalizeVector(vector: DoubleArray, norm: Double): DoubleArray =
        vector.map { it / norm }.toDoubleArray()

    /**
     * Extension function to extract a column from a `D2Array<Double>` as a `DoubleArray`.
     *
     * @param colIndex The index of the column to extract.
     * @return A `DoubleArray` representing the specified column.
     */
    private fun D2Array<Double>.getColumn(colIndex: Int): DoubleArray =
        DoubleArray(this.shape[0]) { rowIndex -> this[rowIndex, colIndex] }

    /**
     * Performs modified Gram-Schmidt orthogonalization on a given matrix.
     *
     * This function takes a matrix `inputMatrixA` with dimensions (m x n) and calculates
     * two matrices `Q` and `R` such that: `inputMatrixA = Q * R`. Here, `Q` is an orthogonal
     * matrix and `R` is an upper triangular matrix.
     *
     * The method is based on the classic Gram-Schmidt process, in which each column of the input matrix
     * is orthogonalized against the previous ones and then normalized. This implementation uses the
     * modified version of Gram-Schmidt for improved numerical stability.
     *
     * Before computation, the function validates that:
     * - The input matrix is two-dimensional and non-empty.
     * - The number of rows is greater than or equal to the number of columns (m >= n),
     *   which is required to produce a full-rank orthogonal basis.
     *
     * @param inputMatrixA The input matrix with `Double` values whose columns are to be orthogonalized.
     * @return A `Pair` consisting of:
     *   - `q`: a matrix with orthonormal columns
     *   - `r`: an upper triangular matrix with the projection coefficients and norms
     * @throws IllegalArgumentException if the matrix has empty dimensions or more columns than rows.
     */
    private fun gramSchmidt(inputMatrixA: D2Array<Double>): Pair<D2Array<Double>, D2Array<Double>> {
        val numRows = inputMatrixA.shape[0]
        val numCols = inputMatrixA.shape[1]

        require(numRows > 0 && numCols > 0) {
            "The input matrix must not have empty dimensions (currently: $numRows rows, $numCols columns)."
        }

        require(numRows >= numCols) {
            "Gram-Schmidt requires m >= n. The input matrix has $numRows rows and $numCols columns."
        }

        val q: D2Array<Double> = mk.zeros(numRows, numCols)
        val r: D2Array<Double> = mk.zeros(numCols, numCols)

        (0 until numCols).forEach { colIndex ->
            var currentVector = extractColumnVector(inputMatrixA, colIndex)

            (0 until colIndex).forEach { prevCol ->
                val projectionCoeff =
                    dotProduct(q.getColumn(prevCol), inputMatrixA.getColumn(colIndex))
                r[prevCol, colIndex] = projectionCoeff
                currentVector =
                    subtractProjection(currentVector, q.getColumn(prevCol), projectionCoeff)
            }

            val norm = kotlin.math.sqrt(currentVector.sumOf { it * it })
            require(norm > 1e-10) { "Column $colIndex is linearly dependent — norm ~ 0." }

            r[colIndex, colIndex] = norm
            val normalized = normalizeVector(currentVector, norm)
            normalized.forEachIndexed { row, value -> q[row, colIndex] = value }
        }

        return Pair(q, r)
    }

    /**
     * Multiplies the transposed matrix `orthogonalMatrix` by the vector `b`.
     *
     * This function calculates the matrix-vector product `Q^T * b`, where `Q^T` is the transpose
     * of an orthogonal matrix and `b` is a column vector. This is a typical step
     * in solving linear systems of equations using QR decomposition, in particular for calculating `Q^T * b`
     * before backward substitution with `R`.
     *
     * @param orthogonalMatrix An orthogonal matrix `Q` with dimensions (m x n).
     * @param b A vector `b` with length m.
     * @return The result of the matrix-vector multiplication `Q^T * b` as a vector of length n.
     */
    private fun transposeMultiply(
        orthogonalMatrix: D2Array<Double>,
        b: D1Array<Double>
    ): D1Array<Double> {
        return orthogonalMatrix.transpose() dot b
    }

    /**
     * Performs backward substitution to solve a linear system of equations.
     *
     * This function solves a system of equations of the form `R * x = y`, where `R` is an upper triangular matrix
     * (by QR decomposition) and `y` is a vector. The solution is obtained by backward substitution,
     * starting from the last row and proceeding upwards to the first.
     *
     * @param r An upper triangular matrix `R` with dimensions (n x n).
     * @param vectorOfTransposedMultiply A vector `y` of length n, the result of `Q^T * b`.
     * @return The solution vector `x` of length n that satisfies the system of equations.
     * @throws IllegalArgumentException if dimensions are inconsistent or diagonal entries are zero.
     */
    private fun backSubstitution(
        r: D2Array<Double>,
        vectorOfTransposedMultiply: D1Array<Double>
    ): D1Array<Double> {
        val numRows = r.shape[0]
        val numCols = r.shape[1]

        require(numRows == numCols) {
            "Matrix must be square (R is $numRows x $numCols)."
        }

        require(vectorOfTransposedMultiply.size == numCols) {
            "Vector length (${vectorOfTransposedMultiply.size}) must match matrix dimensions ($numCols)."
        }

        val solutionVector = mk.zeros<Double>(numCols)

        for (rowIndex in numCols - 1 downTo 0) {
            val sum = (rowIndex + 1 until numCols).sumOf { colIndex ->
                r[rowIndex, colIndex] * solutionVector[colIndex]
            }

            val diagonal = r[rowIndex, rowIndex]
            require(diagonal != 0.0) {
                "Zero on diagonal at row $rowIndex — system cannot be solved (division by zero)."
            }

            solutionVector[rowIndex] =
                (vectorOfTransposedMultiply[rowIndex] - sum) / r[rowIndex, rowIndex]
        }

        return solutionVector
    }

    /**
     * Calculates the solution to a linear least squares problem using QR decomposition.
     *
     * This function solves an overdetermined linear equation system of the form `A * x ~ b`,
     * where `A` is a matrix with more rows than columns. The solution minimizes the error
     * in the sense of least squares.
     *
     * The algorithm uses the QR decomposition of `A` using modified Gram-Schmidt orthogonalization:
     * - `A = Q * R`, where `Q` is an orthogonal matrix and `R` is an upper triangular matrix.
     * - Then `Q^T * b` is calculated.
     * - The solution `x` is obtained by backward substitution from `R * x = Q^T * b`.
     *
     * @param a The input matrix `A` with dimensions (m x n), where m >= n.
     * @param b The target vector `b` with length m.
     * @return The solution vector `x` of length n, which represents the least squares solution.
     */
    fun leastSquaresQR(
        a: D2Array<Double>,
        b: D1Array<Double>
    ): D1Array<Double> {
        val (q, r) = gramSchmidt(a)
        val y = transposeMultiply(q, b)
        return backSubstitution(r, y)
    }

    /**
     * Calculates the solution to the least squares problem Ax = b, aka. linear system of equations
     * This method finds the vector x that minimizes the Euclidean 2-norm ||Ax - b||^2. (-> "least squares")
     * To solve for x, the normal equation (A^T * A)x = A^T * b is used.
     * To improve numerical stability and prevent overfitting, this implementation uses Tikhonov regularization (also known as ridge regression).
     * A small regularization term (lambda * I) is added to the A^T * A matrix, making the equation
     * (A^T * A + lambda * I)x = A^T * b. This ensures the matrix is invertible.
     *
     * @param A The design matrix (m x n), where m is the number of observations and n is the number of features.
     * @param b The vector of observed values (m-dimensional).
     * @param regularization The regularization parameter (lambda), a small positive value to ensure the matrix is well-conditioned.
     * @return The vector x (n-dimensional) that represents the least squares solution, typically the coefficients of the linear model.
     */
    fun leastSquaresTikhonov(
        A: D2Array<Double>,
        b: D1Array<Double>,
        regularization: Double = 1e-6
    ): D1Array<Double> {
        require(A.shape[0] == b.shape[0]) { "Matrix and vector for least squares must have same number of rows (observations)." }

        val matrixAt = A.transpose() //compute A^T
        val matrixAtA = mk.linalg.dot(matrixAt, A) //compute matrix A^T * A.
        val vectorAtb = mk.linalg.dot(matrixAt, b) //compute vector A^T * b

        //apply Tikhonov regularization/ridge regression.
        //add small offset to diagonal (lambda * Identity) of matrix A^T * A.
        //this ensures that the matrix (A^T * A + lambda * I) is invertible.

        //do ridge regularization for all features except the last one, which is a constant bias
        val P = mk.identity<Double>(matrixAtA.shape[0])
        val unregularizedIndex = matrixAtA.shape[0] - 1
        P[unregularizedIndex, unregularizedIndex] = 0.0

        val regularizedAtA = matrixAtA + P * regularization

        //solve regularized system of linear equations (A^T * A + lambda * I)x = A^T * b for x.
        return mk.linalg.solve(regularizedAtA, vectorAtb)
    }
}