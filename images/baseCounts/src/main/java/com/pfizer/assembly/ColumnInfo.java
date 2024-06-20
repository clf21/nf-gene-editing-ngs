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
import java.util.EnumSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.SortedMap;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.jcvi.jillion.core.Direction;
import org.jcvi.jillion.core.residue.nt.Nucleotide;
import org.jcvi.jillion.core.util.MapValueComparator;
import org.jcvi.jillion.core.util.SingleThreadAdder;

import com.pfizer.sunspots.features.assembly.AssemblyFeature;
import com.pfizer.sunspots.features.assembly.VariationBelowThreshold;

import lombok.ToString;

@ToString
public class ColumnInfo{
	private final Nucleotide ref;
	private final int gappedOffset, ungappedOffset;
	private final Map<Nucleotide, SingleThreadAdder> fwdCounts = new EnumMap<>(Nucleotide.class);
	private final Map<Nucleotide, SingleThreadAdder> revCounts= new EnumMap<>(Nucleotide.class);
	
	public ColumnInfo(Nucleotide ref, int gappedOffset, int ungappedOffset) {
		this.ref= ref;
		this.gappedOffset= gappedOffset;
		this.ungappedOffset = ungappedOffset;
	}
	
	public void addRead(Nucleotide n, Direction dir) {
		if(dir==Direction.FORWARD) {
			fwdCounts.computeIfAbsent(n, k-> new SingleThreadAdder()).increment();
		}else {
			revCounts.computeIfAbsent(n, k-> new SingleThreadAdder()).increment();
		}
	}
	public int getGappedOffset() {
		return gappedOffset;
	}
	public int getUngappedOffset() {
		return ungappedOffset;
	}
	
	
	public boolean hasVariants() {
		
		//for now assume gap consensus call is no
		if(ref.isGap()) {
			return false;
		}
		long totalDepth=0;
		long nonGapOrNDepth = 0;
		long mismatchCount=0;
		for(Map.Entry<Nucleotide, SingleThreadAdder> entry : fwdCounts.entrySet()) {
			totalDepth += entry.getValue().longValue();
			if(!entry.getKey().isGap() && entry.getKey() !=Nucleotide.Unknown) {
				nonGapOrNDepth += entry.getValue().longValue();
				if(entry.getKey() != ref) {
					mismatchCount += entry.getValue().longValue();
				}
			}
		}
		for(Map.Entry<Nucleotide, SingleThreadAdder> entry : revCounts.entrySet()) {
			totalDepth += entry.getValue().longValue();
			if(!entry.getKey().isGap() && entry.getKey() !=Nucleotide.Unknown) {
				nonGapOrNDepth += entry.getValue().longValue();
				if(entry.getKey() != ref) {
					mismatchCount += entry.getValue().longValue();
				}
			}
		}
		if(totalDepth < 100) {
			return false;
		}
		double percentMisMatch = ((double)mismatchCount)/nonGapOrNDepth ;
		return  percentMisMatch >=0.05D;
	}

	public Nucleotide getRef() {
		return ref;
	}

	public Map<Nucleotide, SingleThreadAdder> getFwdCounts() {
		return fwdCounts;
	}

	public Map<Nucleotide, SingleThreadAdder> getRevCounts() {
		return revCounts;
	}

	public long getTotalDepth() {
		long totalDepth=0;
		long nonGapOrNDepth = 0;
		long mismatchCount=0;
		for(Map.Entry<Nucleotide, SingleThreadAdder> entry : fwdCounts.entrySet()) {
			totalDepth += entry.getValue().longValue();
			if(!entry.getKey().isGap() && entry.getKey() !=Nucleotide.Unknown) {
				nonGapOrNDepth += entry.getValue().longValue();
				if(entry.getKey() != ref) {
					mismatchCount += entry.getValue().longValue();
				}
			}
		}
		for(Map.Entry<Nucleotide, SingleThreadAdder> entry : revCounts.entrySet()) {
			totalDepth += entry.getValue().longValue();
			if(!entry.getKey().isGap() && entry.getKey() !=Nucleotide.Unknown) {
				nonGapOrNDepth += entry.getValue().longValue();
				if(entry.getKey() != ref) {
					mismatchCount += entry.getValue().longValue();
				}
			}
		}
		//TODO should we count -s towards Depth?
		return totalDepth;
	}
	public Stream<VariantCandidate> toVariantCandiates(double percentThreshold) {
		return toVariantCandiates(percentThreshold, null);
	}
	public Stream<VariantCandidate> toVariantCandiates(double percentThreshold, Consumer<AssemblyFeature> featureConsumer) {
		//for now assume gap consensus call is no
				if(getRef().isGap()) {
					return Stream.empty();
				}
				if(getFwdCounts().isEmpty() && getRevCounts().isEmpty()) {
					return Stream.empty();
				}
				int totalDepth=0;
				int nonGapOrNDepth = 0;
				int mismatchCount=0;
				Set<Nucleotide> misMatches =  EnumSet.noneOf(Nucleotide.class);
				Map<Nucleotide, SingleThreadAdder> totalMap = new EnumMap<>(Nucleotide.class);
				
				for(Map.Entry<Nucleotide, SingleThreadAdder> entry : getFwdCounts().entrySet()) {
					totalDepth += entry.getValue().intValue();
					totalMap.put(entry.getKey(), new SingleThreadAdder(entry.getValue().longValue()));
					
					if(!entry.getKey().isGap() && entry.getKey() !=Nucleotide.Unknown) {
						nonGapOrNDepth += entry.getValue().intValue();
						if(entry.getKey() != getRef()) {
							mismatchCount += entry.getValue().intValue();
							misMatches.add(entry.getKey());
						}
					}
				}
				for(Map.Entry<Nucleotide, SingleThreadAdder> entry : getRevCounts().entrySet()) {
					totalDepth += entry.getValue().intValue();
					totalMap.computeIfAbsent(entry.getKey(), k -> new SingleThreadAdder()).add(entry.getValue().longValue());
					
					if(!entry.getKey().isGap() && entry.getKey() !=Nucleotide.Unknown) {
						nonGapOrNDepth += entry.getValue().intValue();
						if(entry.getKey() != getRef()) {
							mismatchCount += entry.getValue().intValue();
							misMatches.add(entry.getKey());
						}
					}
				}
				SortedMap<Nucleotide, SingleThreadAdder> sortedTotalMap = MapValueComparator.sortDescending(totalMap);
				Iterator<Nucleotide> iter = sortedTotalMap.keySet().iterator();
				
				//we've already checked for 0x coverage so there must be something to iterate over
				Nucleotide mostFreq = iter.next();
				if(mostFreq.isGap()) {
					//ignore?
					return Stream.empty();
				}
				sortedTotalMap.remove(Nucleotide.Gap);
				double effectivelyFinalTotalDepth = totalDepth;
				sortedTotalMap.entrySet().removeIf(entry ->{ 
					double p = entry.getValue().longValue() / effectivelyFinalTotalDepth;
					
					boolean f = p < percentThreshold;
					if(featureConsumer !=null && f) {
						featureConsumer.accept(VariationBelowThreshold.builder()
								.candidateBase(entry.getKey())
								.majorBase(mostFreq)
								.candidatePercent(p)
								.gappedNucleotideOffset(getGappedOffset())
								.fwdCounts(getFwdCounts())
								.revCounts(getRevCounts())
								.requiredThreshold(percentThreshold)
								.build());
					}
					return f;
					});
				Iterator<Entry<Nucleotide,SingleThreadAdder>> entryIter = sortedTotalMap.entrySet().iterator();
				entryIter.next(); //skip most freq again
				if(!entryIter.hasNext()) {
					return Stream.empty();
				}
				int finalTotalDepth = totalDepth;
				//size is -1 because we removed the most frequent base
				//AND apparently Stream.count() just uses this value without inspecting the stream?
				return StreamSupport.stream(Spliterators.spliterator(entryIter, sortedTotalMap.size()-1, 
						Spliterator.SIZED | Spliterator.CONCURRENT | Spliterator.IMMUTABLE ), false)
							.map(n ->  VariantCandidate.builder()
									.candidate(n.getKey())
									.majorBase(mostFreq)
									.percent(n.getValue().longValue() / effectivelyFinalTotalDepth)
									.reference(getRef())
									.gappedOffset(getGappedOffset())
									.ungappedOffset(getUngappedOffset())
									.fwdCounts(getFwdCounts())
									.revCounts(getRevCounts())
									.coverageDepth(finalTotalDepth)
									.build());

	}

	public Map<Nucleotide, SingleThreadAdder> getTotalCounts() {
		Map<Nucleotide, SingleThreadAdder> totalMap = new EnumMap<>(Nucleotide.class);
		
		for(Map.Entry<Nucleotide, SingleThreadAdder> entry : getFwdCounts().entrySet()) {
			totalMap.put(entry.getKey(), new SingleThreadAdder(entry.getValue().longValue()));
			
		}
		for(Map.Entry<Nucleotide, SingleThreadAdder> entry : getRevCounts().entrySet()) {
		
			totalMap.computeIfAbsent(entry.getKey(), k -> new SingleThreadAdder()).add(entry.getValue().longValue());
			
		}
		return totalMap;
		
	}
	
	public Optional<Nucleotide> getMostFrequentBase(){
		if(fwdCounts.size()==0 && revCounts.size()==0) {
			return Optional.empty();
		}
		return getMostFrequent(getTotalCounts());
		
	}

	public Optional<Nucleotide> getMostFrequentForwardBase(){
		if(fwdCounts.size()==0) {
			return Optional.empty();
		}
		return getMostFrequent(fwdCounts);
		
	}
	public Optional<Nucleotide> getMostFrequentReverseBase(){
		if(revCounts.size()==0) {
			return Optional.empty();
		}
		return getMostFrequent(revCounts);
		
	}
	private Optional<Nucleotide> getMostFrequent(Map<Nucleotide, SingleThreadAdder> map) {
		long max=-1;
		Nucleotide n=null;
		
		for(Entry<Nucleotide, SingleThreadAdder> entry:  map.entrySet()) {
			if(entry.getValue().longValue() > max) {
				max = entry.getValue().longValue();
				n= entry.getKey();
			}
		}
		return Optional.ofNullable(n);
	}
	
}