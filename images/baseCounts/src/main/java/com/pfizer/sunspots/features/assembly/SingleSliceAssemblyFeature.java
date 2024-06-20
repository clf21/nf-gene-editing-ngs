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

import org.jcvi.jillion.core.residue.nt.Nucleotide;
import org.jcvi.jillion.core.util.SingleThreadAdder;

public interface SingleSliceAssemblyFeature extends AssemblyFeature{

	int getReferencePosition();
	
	void setReferencePosition(int refPostion);
	
	Map<Nucleotide, SingleThreadAdder> getFwdCounts();

	Map<Nucleotide, SingleThreadAdder> getRevCounts();

	int getCoverageDepth();


	Nucleotide getMajorBase();
	Nucleotide getCandidateBase();

	int getGappedNucleotideOffset();
	
	double getPercent();
}
