package org.htwk.pacing.backend.predictor.linalg

import org.jetbrains.kotlinx.multik.api.linalg.dot
import org.jetbrains.kotlinx.multik.api.linalg.solve
import org.jetbrains.kotlinx.multik.api.mk
import org.jetbrains.kotlinx.multik.api.ndarray
import org.jetbrains.kotlinx.multik.ndarray.data.D1Array
import org.jetbrains.kotlinx.multik.ndarray.data.D2Array

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

    fun main() {
        val A = mk.ndarray(
            mk[
                mk[1.0, 1.0],
                mk[2.0, 1.0],
                mk[3.0, 1.0],
                mk[4.0, 1.0],
                mk[5.0, 1.0]
            ]
        ) //5x2 Matrix

        val b = mk.ndarray(mk[2.0, 2.9, 3.8, 4.1, 5.2]) //Target Vector

        val x = leastSquares(A, b)
        println("Least-Squares Solution: $x")
    }
}