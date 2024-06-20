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

import org.jcvi.jillion.core.Direction;
import org.jcvi.jillion.core.residue.nt.Nucleotide;

import java.util.List;
import java.util.stream.Collectors;

public class ColumnInfoDSL {
	private final ColumnInfo info;
	
	public ColumnInfoDSL() {
		this(Nucleotide.Unknown, 0);
	}
	public ColumnInfoDSL(String ref, long offset) {
		this(Nucleotide.parse(ref), offset);
	}
	public ColumnInfoDSL(char ref, long offset) {
		this(Nucleotide.parse(ref), offset);
	}
	public ColumnInfoDSL(Nucleotide ref, long offset) {
		this.info = new ColumnInfo(ref, (int) offset, (int) offset);
		
	}
	public ColumnInfoDSL addForward(String n, int times) {
		return add(Nucleotide.parse(n), Direction.FORWARD, times);
	}
	public ColumnInfoDSL addForward(char n, int times) {
		return add(Nucleotide.parse(n), Direction.FORWARD, times);
	}
	
	public ColumnInfoDSL addReverse(String n, int times) {
		return add(Nucleotide.parse(n), Direction.REVERSE, times);
	}
	public ColumnInfoDSL addReverse(char n, int times) {
		return add(Nucleotide.parse(n), Direction.REVERSE, times);
	}
	
	public ColumnInfoDSL addForward(Nucleotide n, int times) {
		return add(n, Direction.FORWARD, times);
	}
	public ColumnInfoDSL addReverse(Nucleotide n, int times) {
		return add(n, Direction.REVERSE, times);
	}
	public ColumnInfoDSL add(Nucleotide n, Direction dir, int times) {
		for(int i=0; i< times; i++) {
			info.addRead(n, dir);
		}
		return this;
	}
	
	public ColumnInfo build() {
		return info;
	}
	
	public List<VariantCandidate> toVariantCandidates(){
		return toVariantCandidates(0);
	}
	public List<VariantCandidate> toVariantCandidates(double percentVariant){
		return info.toVariantCandiates(percentVariant).collect(Collectors.toList());
	}
}