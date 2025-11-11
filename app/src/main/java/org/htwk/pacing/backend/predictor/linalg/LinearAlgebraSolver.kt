package org.htwk.pacing.backend.predictor.linalg

import org.jetbrains.kotlinx.multik.api.linalg.dot
import org.jetbrains.kotlinx.multik.api.mk
import org.jetbrains.kotlinx.multik.api.zeros
import org.jetbrains.kotlinx.multik.ndarray.data.D1Array
import org.jetbrains.kotlinx.multik.ndarray.data.D2Array
import org.jetbrains.kotlinx.multik.ndarray.data.get
import org.jetbrains.kotlinx.multik.ndarray.data.set
object LinearAlgebraSolver {

    // Gram-Schmidt algorithm for calculating the QR decomposition
    private fun gramSchmidt (inputMatrixA: D2Array<Double>): Pair<D2Array<Double>, D2Array<Double>> {
        val numRows = inputMatrixA.shape[0]
        val numCols = inputMatrixA.shape[1]

        val q: D2Array<Double> = mk.zeros(numRows, numCols)
        val r: D2Array<Double> = mk.zeros(numCols, numCols)

        // Loop over the columns of the input matrix
        for (colIndex in 0 until numCols) {
            // Copy current column
            val currentVector = DoubleArray(numRows) { rowIndex -> inputMatrixA[rowIndex, colIndex] }

            // Orthogonalization of the current column against all previous columns
            for (previousColIndex in 0 until colIndex) {
                // Calculate projection
                var projectionCoeff = 0.0
                for (rowIndex in 0 until numRows) {
                    projectionCoeff += q[rowIndex, previousColIndex] * inputMatrixA[rowIndex, colIndex]
                }
                // Save projection entry
                r[previousColIndex, colIndex] = projectionCoeff

                // Subtracting the projection
                for (rowIndex in 0 until numRows) {
                    currentVector[rowIndex] -= projectionCoeff * q[rowIndex, previousColIndex]
                }
            }

            // Norm calculation of the orthogonalized vector
            val norm = kotlin.math.sqrt(currentVector.sumOf { it * it })

            // Set diagonal entry
            r[colIndex, colIndex] = norm


            // Normalization of the orthogonal matrix
            for (rowIndex in 0 until numRows) {
                q[rowIndex, colIndex] = currentVector[rowIndex] / norm
            }
        }

        return Pair(q, r)
    }

    private fun transposeMultiply(orthogonalMatrix: D2Array<Double>, b: D1Array<Double>): D1Array<Double>{
        return orthogonalMatrix.transpose() dot b
    }

    // Backward substitution to solve the system Rx = Q^T b
    private fun backSubstitution(upperTriangleMatrix: D2Array<Double>, vektorOfTransposedMultiply: D1Array<Double>): D1Array<Double>{
        val numCols = upperTriangleMatrix.shape[1]
        val solutionVector = mk.zeros<Double>(numCols)

        for (rowIndex in numCols - 1 downTo 0) {
            var sum = 0.0

            for (colIndex in rowIndex + 1 until numCols) {
                sum += upperTriangleMatrix[rowIndex, colIndex] * solutionVector[colIndex]
            }

            solutionVector[rowIndex] = (vektorOfTransposedMultiply[rowIndex] - sum) / upperTriangleMatrix[rowIndex, rowIndex]
        }

        return solutionVector
    }
    fun leastSquares(
        a: D2Array<Double>,
        b: D1Array<Double>
    ): D1Array<Double> {
        val (q, r) = gramSchmidt(a)
        val y = transposeMultiply(q, b)
        return backSubstitution(r, y)
    }
}