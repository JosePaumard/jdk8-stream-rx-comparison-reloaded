/*
 * Copyright (C) 2019 José Paumard
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.paumard.jdk8.rxjava;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Warmup;


import org.paumard.jdk8.bench.ShakespearePlaysScrabble;
import org.paumard.jdk8.util.IterableSpliterator;
import rx.Observable;
import rx.functions.Func1;

public class ShakespearePlaysScrabbleWithRxJava extends ShakespearePlaysScrabble {

	interface Wrapper<T> {
		T get() ;
		
		default Wrapper<T> set(T t) {
			return () -> t ;
		}
	}
	
	interface IntWrapper {
		int get() ;
		
		default IntWrapper set(int i) {
			return () -> i ;
		}
		
		default IntWrapper incAndSet() {
			return () -> get() + 1 ;
		}
	}
	
	interface LongWrapper {
		long get() ;
		
		default LongWrapper set(long l) {
			return () -> l ;
		}
		
		default LongWrapper incAndSet() {
			return () -> get() + 1L ;
		}
		
		default LongWrapper add(LongWrapper other) {
			return () -> get() + other.get() ;
		}
	}

	/*
    Result: 12,690 ±(99.9%) 0,148 s/op [Average]
    		  Statistics: (min, avg, max) = (12,281, 12,690, 12,784), stdev = 0,138
    		  Confidence interval (99.9%): [12,543, 12,838]
    		  Samples, N = 15
    		        mean =     12,690 ±(99.9%) 0,148 s/op
    		         min =     12,281 s/op
    		  p( 0,0000) =     12,281 s/op
    		  p(50,0000) =     12,717 s/op
    		  p(90,0000) =     12,784 s/op
    		  p(95,0000) =     12,784 s/op
    		  p(99,0000) =     12,784 s/op
    		  p(99,9000) =     12,784 s/op
    		  p(99,9900) =     12,784 s/op
    		  p(99,9990) =     12,784 s/op
    		  p(99,9999) =     12,784 s/op
    		         max =     12,784 s/op


    		# Run complete. Total time: 00:06:26

    		Benchmark                                               Mode  Cnt   Score   Error  Units
    		ShakespearePlaysScrabbleWithRxJava.measureThroughput  sample   15  12,690 ± 0,148   s/op   
    		
    		Benchmark                                              Mode  Cnt       Score      Error  Units
			ShakespearePlaysScrabbleWithRxJava.measureThroughput   avgt   15  250074,776 ± 7736,734  us/op
			ShakespearePlaysScrabbleWithStreams.measureThroughput  avgt   15   29389,903 ± 1115,836  us/op
    		
    */ 
    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    @Warmup(
		iterations=20
    )
    @Measurement(
    	iterations=20
    )
    @Fork(5)
    public List<Entry<Integer, List<String>>> measureThroughput() throws InterruptedException {

        // Function to compute the score of a given word
    	Func1<Integer, Observable<Integer>> scoreOfALetter = letter -> Observable.just(letterScores[letter - 'a']) ;
            
        // score of the same letters in a word
        Func1<Entry<Integer, LongWrapper>, Observable<Integer>> letterScore =
        		entry -> 
        			Observable.just(
    					letterScores[entry.getKey() - 'a']*
    					Integer.min(
    	                        (int)entry.getValue().get(), 
    	                        (int)scrabbleAvailableLetters[entry.getKey() - 'a']
    	                    )
        	        ) ;
        
        Func1<String, Observable<Integer>> toIntegerObservable =
        		string -> Observable.from(IterableSpliterator.of(string.chars().boxed().spliterator())) ;
                    
        // Histogram of the letters in a given word
        Func1<String, Observable<HashMap<Integer, LongWrapper>>> histoOfLetters =
        		word -> toIntegerObservable.call(word)
        					.collect(
    							() -> new HashMap<Integer, LongWrapper>(),
    							(HashMap<Integer, LongWrapper> map, Integer value) ->
    								{ 
    									LongWrapper newValue = map.get(value) ;
    									if (newValue == null) {
    										newValue = () -> 0L ;
    									}
    									map.put(value, newValue.incAndSet()) ;
    								}
    								
        					) ;
                
        // number of blanks for a given letter
        Func1<Entry<Integer, LongWrapper>, Observable<Long>> blank =
        		entry ->
        			Observable.just(
	        			Long.max(
	        				0L, 
	        				entry.getValue().get() - 
	        				scrabbleAvailableLetters[entry.getKey() - 'a']
	        			)
        			) ;

        // number of blanks for a given word
        Func1<String, Observable<Long>> nBlanks =
        		word -> histoOfLetters.call(word)
        					.flatMap(map -> Observable.from(() -> map.entrySet().iterator()))
        					.flatMap(blank)
        					.reduce(Long::sum) ;
        					
                
        // can a word be written with 2 blanks?
        Func1<String, Observable<Boolean>> checkBlanks =
        		word -> nBlanks.call(word)
        					.flatMap(l -> Observable.just(l <= 2L)) ;
        
        // score taking blanks into account letterScore1
        Func1<String, Observable<Integer>> score2 =
        		word -> histoOfLetters.call(word)
        					.flatMap(map -> Observable.from(() -> map.entrySet().iterator()))
        					.flatMap(letterScore)
        					.reduce(Integer::sum) ;
        					
        // Placing the word on the board
        // Building the streams of first and last letters
        Func1<String, Observable<Integer>> first3 =
        		word -> Observable.from(IterableSpliterator.of(word.chars().boxed().limit(3).spliterator())) ;
        Func1<String, Observable<Integer>> last3 =
        		word -> Observable.from(IterableSpliterator.of(word.chars().boxed().skip(3).spliterator())) ;
        		
        
        // Stream to be maxed
        Func1<String, Observable<Integer>> toBeMaxed =
        	word -> Observable.just(first3.call(word), last3.call(word))
        				.flatMap(observable -> observable) ;
            
        // Bonus for double letter
        Func1<String, Observable<Integer>> bonusForDoubleLetter =
        	word -> toBeMaxed.call(word)
        				.flatMap(scoreOfALetter)
        				.reduce(Integer::max) ;
            
        // score of the word put on the board
        Func1<String, Observable<Integer>> score3 =
        	word ->
        		Observable.just(
        				score2.call(word), 
        				score2.call(word), 
        				bonusForDoubleLetter.call(word), 
        				bonusForDoubleLetter.call(word), 
        				Observable.just(word.length() == 7 ? 50 : 0)
        		)
        		.flatMap(observable -> observable)
        		.reduce(Integer::sum) ;

        Func1<Func1<String, Observable<Integer>>, Observable<TreeMap<Integer, List<String>>>> buildHistoOnScore =
        		score -> Observable.from(() -> shakespeareWords.iterator())
        						.filter(scrabbleWords::contains)
        						.filter(word -> checkBlanks.call(word).toBlocking().first())
        						.collect(
        							() -> new TreeMap<Integer, List<String>>(Comparator.reverseOrder()),
        							(TreeMap<Integer, List<String>> map, String word) -> {
        								Integer key = score.call(word).toBlocking().first() ;
        								List<String> list = map.get(key) ;
        								if (list == null) {
        									list = new ArrayList<String>() ;
        									map.put(key, list) ;
        								}
        								list.add(word) ;
        							}
        						) ;
                
        // best key / value pairs
        List<Entry<Integer, List<String>>> finalList2 =
        		buildHistoOnScore.call(score3)
        			.flatMap(map -> Observable.from(() -> map.entrySet().iterator()))
        			.take(3)
        			.collect(
        				() -> new ArrayList<Entry<Integer, List<String>>>(),
        				(list, entry) -> {
        					list.add(entry) ;
        				}
        			)
        			.toBlocking()
        			.first() ;
        			
        
//        System.out.println(finalList2);
        
        return finalList2 ;
    }
}
