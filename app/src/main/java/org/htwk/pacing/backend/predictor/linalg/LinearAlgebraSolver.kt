package org.htwk.pacing.backend.predictor.linalg

import org.htwk.pacing.backend.predictor.linalg.LinearAlgebraSolver.leastSquares
import org.htwk.pacing.backend.predictor.linalg.LinearAlgebraSolver.leastSquares2
import org.htwk.pacing.backend.predictor.model.LinearCombinationPredictionModel
import org.jetbrains.kotlinx.multik.api.linalg.dot
import org.jetbrains.kotlinx.multik.api.linalg.solve
import org.jetbrains.kotlinx.multik.api.mk
import org.jetbrains.kotlinx.multik.api.ndarray
import org.jetbrains.kotlinx.multik.api.zeros
import org.jetbrains.kotlinx.multik.ndarray.data.D1Array
import org.jetbrains.kotlinx.multik.ndarray.data.D2Array
import org.jetbrains.kotlinx.multik.ndarray.data.get
import org.jetbrains.kotlinx.multik.ndarray.data.set
import org.jetbrains.kotlinx.multik.ndarray.operations.map
import org.jetbrains.kotlinx.multik.ndarray.operations.minus
import org.jetbrains.kotlinx.multik.ndarray.operations.sum
import org.jetbrains.kotlinx.multik.ndarray.operations.toList

object LinearAlgebraSolver {
    fun leastSquares(A: D2Array<Double>, b: D1Array<Double>): D1Array<Double> {
        val transposedA = A.transpose()                                 // (A^T)
        val transposedATimesA = mk.linalg.dot(transposedA, A)   // (A^T) * A
        val transposedATimesb = mk.linalg.dot(transposedA, b)       // (A^T) * b
        val x =
            mk.linalg.solve(
                transposedATimesA,
                transposedATimesb
            )  //Solution of ((A^T)*A)*x = (A^T)*b
        return x
    }

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
                q[rowIndex, colIndex] = currentVector[rowIndex] / norm // Normalisiere u und speichere es in Q
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
    fun leastSquares2(
        a: D2Array<Double>,
        b: D1Array<Double>
    ): D1Array<Double> {
        val (q, r) = LinearCombinationPredictionModel.gramSchmidt(a)
        val y = transposeMultiply(q, b)
        return backSubstitution(r, y)
    }


}
fun main() {
    val A = mk.ndarray(
        mk[
            mk[5.0, 1.0, 6.0, 3.0, 0.0, 4.0],
            mk[8.0, 3.0, 8.0, 2.0, 0.0, 9.0],
            mk[9.0, 5.0, 8.0, 8.0, 6.0, 1.0],
            mk[2.0, 2.0, 4.0, 4.0, 4.0, 8.0],
            mk[8.0, 3.0, 1.0, 7.0, 7.0, 5.0],
            mk[2.0, 7.0, 0.0, 9.0, 9.0, 9.0],
            mk[6.0, 4.0, 6.0, 9.0, 3.0, 0.0],
            mk[8.0, 3.0, 2.0, 4.0, 0.0, 3.0],
            mk[1.0, 8.0, 4.0, 7.0, 8.0, 6.0],
            mk[3.0, 1.0, 4.0, 2.0, 3.0, 1.0],
            mk[4.0, 7.0, 1.0, 9.0, 8.0, 1.0],
            mk[1.0, 1.0, 8.0, 8.0, 7.0, 0.0],
            mk[9.0, 5.0, 8.0, 3.0, 9.0, 0.0],
            mk[5.0, 0.0, 9.0, 5.0, 6.0, 2.0],
            mk[7.0, 3.0, 0.0, 3.0, 3.0, 3.0],
            mk[9.0, 2.0, 5.0, 0.0, 7.0, 4.0],
            mk[1.0, 7.0, 8.0, 9.0, 3.0, 6.0],
            mk[8.0, 9.0, 7.0, 0.0, 8.0, 5.0],
            mk[1.0, 6.0, 6.0, 3.0, 0.0, 8.0],
            mk[2.0, 1.0, 6.0, 3.0, 6.0, 5.0],
            mk[1.0, 4.0, 8.0, 7.0, 1.0, 1.0],
            mk[0.0, 2.0, 6.0, 5.0, 0.0, 3.0],
            mk[0.0, 6.0, 5.0, 5.0, 1.0, 5.0],
            mk[3.0, 7.0, 0.0, 5.0, 1.0, 6.0],
            mk[6.0, 4.0, 8.0, 5.0, 0.0, 4.0],
            mk[2.0, 8.0, 7.0, 4.0, 7.0, 6.0],
            mk[1.0, 9.0, 0.0, 8.0, 5.0, 0.0],
            mk[3.0, 8.0, 4.0, 2.0, 2.0, 6.0],
            mk[6.0, 0.0, 3.0, 6.0, 8.0, 8.0],
            mk[8.0, 5.0, 3.0, 7.0, 7.0, 9.0],
            mk[7.0, 5.0, 3.0, 4.0, 7.0, 7.0],
            mk[7.0, 6.0, 4.0, 4.0, 1.0, 5.0],
            mk[7.0, 4.0, 6.0, 3.0, 7.0, 4.0],
            mk[6.0, 5.0, 4.0, 4.0, 6.0, 6.0],
            mk[5.0, 4.0, 6.0, 7.0, 6.0, 5.0],
            mk[7.0, 0.0, 2.0, 1.0, 3.0, 5.0],
            mk[5.0, 4.0, 3.0, 9.0, 8.0, 1.0],
            mk[4.0, 5.0, 5.0, 5.0, 6.0, 8.0],
            mk[3.0, 1.0, 5.0, 5.0, 4.0, 0.0],
            mk[7.0, 4.0, 5.0, 9.0, 7.0, 3.0],
            mk[6.0, 5.0, 8.0, 1.0, 5.0, 9.0],
            mk[3.0, 6.0, 0.0, 5.0, 5.0, 0.0],
            mk[6.0, 4.0, 5.0, 3.0, 2.0, 9.0],
            mk[3.0, 8.0, 8.0, 7.0, 6.0, 7.0],
            mk[5.0, 3.0, 2.0, 7.0, 1.0, 9.0],
            mk[3.0, 0.0, 5.0, 2.0, 5.0, 5.0],
            mk[9.0, 9.0, 9.0, 3.0, 1.0, 1.0],
            mk[6.0, 5.0, 6.0, 8.0, 8.0, 0.0],
            mk[9.0, 6.0, 9.0, 2.0, 8.0, 9.0],
            mk[9.0, 4.0, 0.0, 7.0, 3.0, 0.0],
            mk[8.0, 7.0, 0.0, 8.0, 3.0, 7.0],
            mk[7.0, 7.0, 8.0, 0.0, 5.0, 7.0],
            mk[7.0, 9.0, 8.0, 3.0, 9.0, 0.0],
            mk[2.0, 9.0, 8.0, 0.0, 8.0, 1.0],
            mk[7.0, 6.0, 0.0, 1.0, 2.0, 1.0],
            mk[9.0, 2.0, 0.0, 1.0, 3.0, 8.0],
            mk[2.0, 9.0, 8.0, 2.0, 7.0, 0.0],
            mk[0.0, 9.0, 8.0, 4.0, 4.0, 2.0],
            mk[3.0, 4.0, 2.0, 5.0, 0.0, 0.0],
            mk[9.0, 0.0, 7.0, 5.0, 5.0, 2.0],
            mk[1.0, 9.0, 7.0, 6.0, 6.0, 3.0],
            mk[0.0, 8.0, 0.0, 7.0, 2.0, 3.0]
        ]
    ) //5x2 Matrix

    val b = mk.ndarray(mk[12.0, 45.0, 3.0, 67.0, 89.0, 23.0, 56.0, 78.0, 90.0, 11.0,
        34.0, 65.0, 87.0, 29.0, 40.0, 72.0, 18.0, 93.0, 55.0, 61.0,
        38.0, 49.0, 77.0, 26.0, 84.0, 31.0, 70.0, 99.0, 14.0, 63.0,
        22.0, 81.0, 36.0, 58.0, 74.0, 19.0, 50.0, 66.0, 42.0, 88.0,
        27.0, 35.0, 60.0, 79.0, 17.0, 46.0, 68.0, 25.0, 85.0, 30.0,
        53.0, 64.0, 21.0, 76.0, 32.0, 59.0, 71.0, 16.0, 43.0, 82.0,
        24.0, 48.0]) //Target Vector

    val startTime = System.nanoTime()
    val x = leastSquares2(A, b)
    val endTime = System.nanoTime() // Endzeit in Nanosekunden
    val duration = endTime - startTime // Dauer in Nanosekunden
    val predicted = A dot x
    val residual = b - predicted
    val squaredError = residual.map { it * it }.sum()
    val mse = squaredError / b.size
    println("Dauer: $duration Nanosekunden")
    println("Least-Squares Solution: $x")
    println("Residuum: ${residual.toList()}")
    println("Quadratische Abweichung: $squaredError")
    println("Mittlere quadratische Abweichung (MSE): $mse")
}