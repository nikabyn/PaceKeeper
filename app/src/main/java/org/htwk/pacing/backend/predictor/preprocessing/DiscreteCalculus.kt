package org.htwk.pacing.backend.predictor.preprocessing

import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

//SEE: ui#38 comment for explanation on this file

fun computeDiscreteIntegral(p: DoubleArray, step: Duration = 10.minutes): DoubleArray {
    val n = p.size
    val stepSec = step.inWholeSeconds

    val integral = DoubleArray(n)
    for (k in 1 until n) {
        integral[k] = integral[k - 1] + 0.5f * (p[k - 1] + p[k]) * stepSec
    }

    return integral
}

fun computeDiscreteDerivative(p: DoubleArray, step: Duration = 10.minutes): DoubleArray {
    val n = p.size
    val stepSec = step.inWholeSeconds

    val derivative = DoubleArray(n)
    if (n > 1) {
        derivative[0] = (p[1] - p[0]) / stepSec
        for (k in 1 until n - 1) derivative[k] = (p[k + 1] - p[k - 1]) / (2f * stepSec)
        derivative[n - 1] = (p[n - 1] - p[n - 2]) / stepSec
    }

    return derivative
}