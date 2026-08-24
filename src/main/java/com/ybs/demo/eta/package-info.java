/**
 * <b>Feature: arrival-time estimation.</b>
 *
 * <p><b>AI topic: reasoning under uncertainty.</b> Segment times are scaled by a
 * hand-collected traffic prior stored as data, and the answer is returned as an interval
 * rather than a point, because a single number would imply a confidence the model does not
 * have.
 *
 * <p>Explicitly <b>not</b> a learned model, and the code says so. The same prior also
 * drives the moving buses in {@code simulation}, so the estimate and the thing being
 * estimated come from one model rather than two.
 */
package com.ybs.demo.eta;
