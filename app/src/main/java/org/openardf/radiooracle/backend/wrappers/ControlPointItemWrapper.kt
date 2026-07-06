/*
 * MIT License
 *
 * Copyright (c) 2025 Pavel Kolský
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package org.openardf.radiooracle.backend.wrappers

import org.openardf.radiooracle.backend.room.entity.ControlPoint

/** UI edit wrapper that tracks control-point SI-code validation state. */
data class ControlPointItemWrapper(
    var controlPoint: ControlPoint,
    var isCodeValid: Boolean,
) {

    companion object {
        /** Wraps control points with optimistic valid flags for the edit UI. */
        fun getWrappers(controlPoints: ArrayList<ControlPoint>): ArrayList<ControlPointItemWrapper> {
            return ArrayList(controlPoints.map { controlPoint ->
                ControlPointItemWrapper(
                    controlPoint.copy(),
                    true
                )
            })
        }

        /** Extracts control points from edit wrappers for persistence. */
        fun getControlPoints(controlPoints: ArrayList<ControlPointItemWrapper>): ArrayList<ControlPoint> {
            return ArrayList(controlPoints.map { it.controlPoint })
        }
    }
}
