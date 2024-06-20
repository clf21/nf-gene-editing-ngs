/******************************************************************************
 * Copyright 2023 Pfizer
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * 		http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/
package com.pfizer.sunspots.features.assembly;

import java.util.Map;

import org.jcvi.jillion.core.Range;
import org.jcvi.jillion.core.residue.nt.Nucleotide;
import org.jcvi.jillion.core.util.SingleThreadAdder;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.ToString;
@Builder
@EqualsAndHashCode
@ToString
public class VariationBelowThreshold implements SingleSliceAssemblyFeature {

	private int referencePosition;
	private final int gappedNucleotideOffset;
	
	private final Map<Nucleotide, SingleThreadAdder> fwdCounts;
	private final Map<Nucleotide, SingleThreadAdder> revCounts;
	
	private final Nucleotide majorBase;
	private final Nucleotide candidateBase;
	
	private final int coverageDepth;
	private final double candidatePercent;
	private final double requiredThreshold;
	
	
	@Override
	public String getEncodedString() {
		return String.format("variant %s is %.2f which is below required %.2f", candidateBase, candidatePercent, requiredThreshold);
	}

	@Override
	public String getFeatureTypeName() {
		return "BELOW %";
	}

	@Override
	public Range getGappedNucleotideRange() {
		return Range.of(gappedNucleotideOffset);
	}

	@Override
	public AssemblyFeature adjustToNewGappedNucleotideOffset(Range newGappedRange) {
		return new VariationBelowThreshold(referencePosition, 
				(int) newGappedRange.getBegin(), 
				fwdCounts, revCounts, 
				majorBase, candidateBase, 
				coverageDepth, candidatePercent, requiredThreshold);
	}

	public Map<Nucleotide, SingleThreadAdder> getFwdCounts() {
		return fwdCounts;
	}

	public Map<Nucleotide, SingleThreadAdder> getRevCounts() {
		return revCounts;
	}

	public int getCoverageDepth() {
		return coverageDepth;
	}

	public void setReferencePosition(int referencePosition) {
		this.referencePosition = referencePosition;
	}

	public Nucleotide getMajorBase() {
		return majorBase;
	}
	public Nucleotide getCandidateBase() {
		return candidateBase;
	}

	public int getGappedNucleotideOffset() {
		return gappedNucleotideOffset;
	}
	
	public static class VariationBelowThresholdBuilder{
		private int referencePosition =1;
	}

	@Override
	public int getReferencePosition() {
		return referencePosition;
	}

	@Override
	public double getPercent() {
		return candidatePercent;
	}

}
