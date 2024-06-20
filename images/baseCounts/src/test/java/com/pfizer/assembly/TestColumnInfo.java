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

import org.jcvi.jillion.core.residue.nt.Nucleotide;
import org.jcvi.jillion.core.util.SingleThreadAdder;
import org.junit.Test;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TestColumnInfo {

	@Test
	public void zeroCoverageGetters() {
		ColumnInfo sut = new ColumnInfoDSL()
							.build();
		
		assertTrue(sut.getFwdCounts().isEmpty());
		assertTrue(sut.getRevCounts().isEmpty());
		assertEquals(0, sut.getTotalDepth());
	}
	
	@Test
	public void addReads() {
		ColumnInfo sut = new ColumnInfoDSL()
							.addForward('A', 10)
							.addReverse('C', 5)
							.build();
		
		assertEquals(counts(Nucleotide.Adenine, 10), sut.getFwdCounts());
		assertEquals(counts(Nucleotide.Cytosine, 5), sut.getRevCounts());
		assertEquals(15, sut.getTotalDepth());
	}
	
	@Test
	public void addMultipleDirectionReads() {
		ColumnInfo sut = new ColumnInfoDSL()
							.addForward('A', 10)
							.addForward('G', 4)
							.addReverse('C', 5)
							.addReverse('T', 7)
							.build();
		
		assertEquals(counts(Nucleotide.Adenine, 10, Nucleotide.Guanine, 4), sut.getFwdCounts());
		assertEquals(counts(Nucleotide.Cytosine, 5, Nucleotide.Thymine, 7), sut.getRevCounts());
		assertEquals(10+4+5+7, sut.getTotalDepth());
	}
	@Test
	public void hasGaps() {
		ColumnInfo sut = new ColumnInfoDSL()
							.addForward('A', 10)
							.addForward('-', 4)
							.addReverse('-', 5)
							.addReverse('T', 7)
							.build();
		
		assertEquals(counts(Nucleotide.Adenine, 10, Nucleotide.Gap, 4), sut.getFwdCounts());
		assertEquals(counts(Nucleotide.Gap, 5, Nucleotide.Thymine, 7), sut.getRevCounts());
		assertEquals(10+4+5+7, sut.getTotalDepth());
	}
	
	@Test
	public void allReadsMatchReferenceShouldHaveNoVariants() {
		ColumnInfo sut = new ColumnInfoDSL('A', 1234)
				.addForward('A', 10)
				.addReverse('A', 7)
				.build();
		
		assertEquals(0L, sut.toVariantCandiates(.05).count());

	}
	@Test
	public void majorityIsGapShouldHaveNoVariants() {
		ColumnInfo sut = new ColumnInfoDSL('A', 1234)
				.addForward('-', 10)
				.addReverse('A', 7)
				.build();
		
		assertEquals(0L, sut.toVariantCandiates(.05).count());

	}
	
	@Test
	public void oneVariant() {
		ColumnInfo sut = new ColumnInfoDSL('A', 1234)
				.addForward('A', 10)
				.addReverse('C', 7)
				.build();
		
		assertEquals(List.of("C"), sut.toVariantCandiates(.05).map(c -> c.getCandidate().toString()).collect(Collectors.toList()));

	}
	@Test
	public void twoVariant() {
		ColumnInfo sut = new ColumnInfoDSL('A', 1234)
				.addForward('A', 10)
				.addReverse('C', 7)
				.addReverse('G', 3)
				.build();
		
		assertEquals(List.of("C", "G"), sut.toVariantCandiates(.05).map(c -> c.getCandidate().toString()).collect(Collectors.toList()));

	}
	@Test
	public void onlyIncludeVariantsThatMeetThreshold() {
		ColumnInfo sut = new ColumnInfoDSL('A', 1234)
				.addForward('A', 90)
				.addReverse('C', 7)
				.addReverse('G', 3)
				.build();
		//C only 3%
		assertEquals(List.of("C"), sut.toVariantCandiates(.05).map(c -> c.getCandidate().toString()).collect(Collectors.toList()));
		assertEquals(List.of("C", "G"), sut.toVariantCandiates(.01).map(c -> c.getCandidate().toString()).collect(Collectors.toList()));

	}
	
	private Map<Nucleotide, SingleThreadAdder> counts(Nucleotide n, int count){
		Map<Nucleotide, SingleThreadAdder> map = new EnumMap<>(Nucleotide.class);
		map.put(n, new SingleThreadAdder(count));
		return map;
	}
	private Map<Nucleotide, SingleThreadAdder> counts(Nucleotide n, int count, Nucleotide n2, int count2){
		Map<Nucleotide, SingleThreadAdder> map = new EnumMap<>(Nucleotide.class);
		map.put(n, new SingleThreadAdder(count));
		map.put(n2, new SingleThreadAdder(count2));
		return map;
	}
}
