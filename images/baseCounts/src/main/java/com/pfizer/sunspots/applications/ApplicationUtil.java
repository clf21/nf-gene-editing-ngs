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
package com.pfizer.sunspots.applications;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

import org.jcvi.jillion.core.io.InputStreamSupplier;
import org.jcvi.jillion.core.util.SingleThreadAdder;
import org.jcvi.jillion.core.util.streams.ThrowingConsumer;

import gov.nih.ncats.common.cli.Cli;
import gov.nih.ncats.common.cli.CliSpecification;
import gov.nih.ncats.common.cli.CliValidationException;
import gov.nih.ncats.common.util.MapUtil;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;

public final class ApplicationUtil {

	private ApplicationUtil() {
		//can not instantiate
	}
	/**
	 * Parse the given command line arguments using the given {@link CliSpecification}
	 * and return the result.  If there are problems parsing or asking for usage,
	 * the appropriate output will be written to either STDOUT or STDERR
	 * before returning.
	 * 
	 * @param spec the {@link CliSpecification} can not be null.
	 * @param args the command line arguments to parse.
	 * @return a new {@link CliResult}.
	 */
	public static CliResult parse(CliSpecification spec, String[] args) {
		if(spec.helpRequested(args)){
			System.out.println(spec.generateUsage());
			System.out.flush();
			return new CliResult(null, true, 0,null);
		}
		try {
			Cli cli= spec.parse(args);
			return new CliResult(cli, false, 0, null);
		} catch (CliValidationException e) {
			System.err.println(e.getMessage());
			e.printStackTrace();
			System.err.println(spec.generateUsage());
			return new CliResult(null, true, -1, e.getMessage());
		}finally {
			System.out.flush();
			System.err.flush();
		}
	}
	
	@Data
	@AllArgsConstructor
	public static class CliResult implements ICliResult{
		private Cli cli;
		@Getter(AccessLevel.NONE)
		private boolean shouldExit;
		private Integer exitCode;
		private String message;
		
		@Override
		public boolean shouldExit() {
			return shouldExit;
		}
	}
	
	public static interface ICliResult{
		boolean shouldExit();
		Integer getExitCode();
		Cli getCli();
		String getMessage();
	}
	
	/**
	 * Parse the given file of ids (may be compressed) into a Set<String>.
	 *  blank lines or lines starting with '#' are ignored.
	 * @param inputFile the input file can not be null and must exist.
	 * @return a new Set, order is not guaranteed.
	 * @throws IOException if problem reading the file
	 * @throws NullPointerException if inputFile is null.
	 */
	public static Set<String> parseLinesIntoSet(File inputFile) throws IOException{
		InputStreamSupplier inputStreamSupplier = InputStreamSupplier.forFile(inputFile);
		SingleThreadAdder count = new SingleThreadAdder();
		consumeStream(inputStreamSupplier, l-> count.increment());
		
		Set<String> ids = new HashSet<>(MapUtil.computeMinHashMapSizeWithoutRehashing(count.longValue()));
		consumeStream(inputStreamSupplier, ids::add);
		return ids;
	}
	
	public static <E extends Throwable> void consumeFileOfLines(File inputFile, ThrowingConsumer<String, E> consumer) throws IOException, E{
		InputStreamSupplier inputStreamSupplier = InputStreamSupplier.forFile(inputFile);
		consumeStream(inputStreamSupplier, consumer);
	}
//	public static void consumeFileOfLines(File inputFile, Consumer<String> consumer) throws IOException{
//		consumeFileOfLines(inputFile, (ThrowingConsumer<String, IOException>) s-> consumer.accept(s));
//	}
	private static <E extends Throwable> void consumeStream(InputStreamSupplier inputStreamSupplier,
			ThrowingConsumer<String, E> consumer) throws IOException, E {
		try(BufferedReader reader = new BufferedReader( new InputStreamReader(inputStreamSupplier.get()))) {
			String line;
			while( (line= reader.readLine()) !=null) {
				if(line.isBlank() || line.startsWith("#")) {
					continue;
				}
				consumer.accept(line);
			}
		}
	}
}
