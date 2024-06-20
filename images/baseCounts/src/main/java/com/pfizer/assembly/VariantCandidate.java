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
package com.pfizer.assembly;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

import org.jcvi.jillion.core.residue.nt.Nucleotide;
import org.jcvi.jillion.core.util.SingleThreadAdder;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class VariantCandidate {

	private static SingleThreadAdder ZERO = new SingleThreadAdder();
	private Nucleotide reference;
	private Nucleotide majorBase;
	private Nucleotide candidate;
	
	private boolean isVariant;
	private final int gappedOffset;
	private final int ungappedOffset;
	private final double percent;
	
	private final Map<Nucleotide, SingleThreadAdder> fwdCounts;
	private final Map<Nucleotide, SingleThreadAdder> revCounts;
	private final int coverageDepth;
	
	public Optional<Variant> toVariant() {
		if(isVariant) {
			return Optional.empty();
		}
		
		return Optional.of( new Variant(reference, candidate, 
				fwdCounts.getOrDefault(candidate, ZERO).intValue() + revCounts.getOrDefault(candidate, ZERO).intValue(),
				gappedOffset, ungappedOffset,
				coverageDepth));
	}

}
