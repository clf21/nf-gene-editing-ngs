package com.pfizer.fgx.gena;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ContainerNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.pfizer.assembly.ColumnInfo;
import com.pfizer.sunspots.applications.ApplicationUtil;
import gov.nih.ncats.common.cli.CliSpecification;
import lombok.*;
import lombok.extern.jackson.Jacksonized;
import org.jcvi.jillion.assembly.AbstractAssemblyTransformer;
import org.jcvi.jillion.assembly.ReadInfo;
import org.jcvi.jillion.core.*;
import org.jcvi.jillion.core.datastore.DataStore;
import org.jcvi.jillion.core.datastore.DataStoreException;
import org.jcvi.jillion.core.datastore.DataStoreProviderHint;
import org.jcvi.jillion.core.datastore.DataStoreUtil;
import org.jcvi.jillion.core.pos.PositionSequence;
import org.jcvi.jillion.core.qual.QualitySequence;
import org.jcvi.jillion.core.residue.nt.INucleotideSequence;
import org.jcvi.jillion.core.residue.nt.Nucleotide;
import org.jcvi.jillion.core.residue.nt.NucleotideSequence;
import org.jcvi.jillion.core.residue.nt.NucleotideSequenceDataStore;
import org.jcvi.jillion.core.util.RangeMap;
import org.jcvi.jillion.core.util.SingleThreadAdder;
import org.jcvi.jillion.fasta.nt.NucleotideFastaDataStore;
import org.jcvi.jillion.fasta.nt.NucleotideFastaFileDataStore;
import org.jcvi.jillion.fasta.nt.NucleotideFastaRecord;
import org.jcvi.jillion.fasta.nt.NucleotideFastaRecordBuilder;
import org.jcvi.jillion.sam.*;
import org.jcvi.jillion.sam.header.SamReferenceSequence;
import org.jcvi.jillion.sam.transform.SamTransformationService;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URI;
import java.nio.file.Files;
import java.sql.Array;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import static gov.nih.ncats.common.cli.CliSpecification.*;
public class FindEditVariants {
    /**
     * Jackson's extension to work with YAML encoded files.
     */
    private static final  ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory());

    /*
    Amplicon/AmpliconInfo structure is not ideal but
    we match the input YAML format for ease of parsing.
     */
    @Value
    @Jacksonized
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AmpliconInfo implements Rangeable{
        NucleotideSequence seq;
        Direction strand;
        String genome;

        String chr;
        long start;
        long end;
        @JsonProperty("guide")
        List<GuideSequence> guides;

        @Override
        public Range asRange(){
            return Range.of(start, end-1);
        }
    }
    @Value
    @Jacksonized
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Amplicon{
        String name;
        AmpliconInfo info;

        /**
         * Parse the input yaml file into the List of {@link Amplicon}s.
         * @param yamlFile the yaml file to parse.
         * @return a List of Amplicons will never be null but could be empty.
         * @throws IOException if there are problems parsing the file.
         */
        public static List<Amplicon> parseYaml(File yamlFile) throws IOException {
            JsonParser yamlParser = YAML_MAPPER.getFactory().createParser(yamlFile);
            List<ObjectNode> list = YAML_MAPPER.readValues(yamlParser, ObjectNode.class).readAll();

            return list.stream()

                    .map(node -> YAML_MAPPER.convertValue(node, Amplicon.class))
                    .collect(Collectors.toList());
        }
    }

    @Value
    @Jacksonized
    @Builder
    public static class GuideSequence{
        @NonNull NucleotideSequence seq;
        @Singular
        Map<@NonNull String, @NonNull  GuideCoordinates> coords;
        @JsonProperty("failed")
        String failedMessage;

        @JsonIgnore
        public boolean isFailed(){
            return failedMessage !=null;
        }

    }
    @Value
    @Jacksonized
    @Builder
    public static class GuideCoordinates implements Rangeable {
        Direction strand;
        long start;
        long end;
        @Override
        public Range asRange(){
            return Range.of(start, end-1);
        }

    }



    @Data
    public static class EditOptions{
        private File bam;
        private File yaml;
        private File outputDir;
        private String prefix;
        private String sampleName;
        int beforeWindow;
        int afterWindow;
    }

    private static DataStore<PartialNucleotideSequence> createPartialReferenceDataStoreFrom(SamParser parser, List<Amplicon> amplicons) throws IOException {
        SamHeaderParser headerParser = new SamHeaderParser();
        parser.parse(headerParser);
        Map<String, PartialNucleotideSequence.Builder> seqMap = new HashMap<>();
        for(SamReferenceSequence refSequence : headerParser.getHeader().getReferenceSequences()){
            seqMap.put(refSequence.getName(), PartialNucleotideSequence.builder(refSequence.getLength()));
        }
        for(Amplicon amplicon : amplicons){
            seqMap.get(amplicon.getInfo().getChr())
                    //range is in bed format
                    .addSequence(amplicon.getInfo().asRange(), amplicon.getInfo().getSeq());
        }
        Map<String, PartialNucleotideSequence> map = new HashMap<>();
        for(Map.Entry<String, PartialNucleotideSequence.Builder> entry : seqMap.entrySet()){
            if(entry.getValue().hasSequences()){
                map.put(entry.getKey(),  entry.getValue().build());
            }
        }

        return DataStoreUtil.adapt(map);
    }
    public static void main(String[] args) throws IOException {

        EditOptions options = new EditOptions();
        CliSpecification spec = CliSpecification.createWithHelp(
                option("bam")
                        .description("path to input BAM file of aligned reads to the amplicon, if there is an indexed .bai file " +
                                "in the same directory it will be auto-detected (required)")
                        .setRequired(true)
                        .setToFile(options::setBam),
                option("yaml")
                        .description("path to input YAML file containing the amplicon and guide sequence and alignment coordinates (required)")
                        .setRequired(true)
                        .setToFile(options::setYaml),
                option("outDir")
                        .description("path to output directory, if not specified files are output to current working directory")

                        .setToFile(options::setOutputDir),
                option("prefix")
                        .description("file output prefix names")

                        .setter(options::setPrefix),
                option("sample")
                        .description("the sample name (required)")
                        .setRequired(true)
                        .setter(options::setSampleName),
                option("before_window")
                        .description("number of bases BEFORE a guide sequence to include in the output")
                        .setToInt(options::setBeforeWindow),
                option("after_window")
                        .description("number of bases AFTER a guide sequence to include in the output")
                        .setToInt(options::setAfterWindow)




        );
        spec.programName("computeBaseCounts")
                .description("Write out the aligned read basecalls at each given amplicon guide sequence in the given bam file.")
                .footer("Created by Danny Katzel")

                .example("-bam /path/to/my.bam -prefix myPrefix -yaml /path/to/my.yaml -outDir /path/to/outputDir",
                        "analyze the given input bam using the amplicon information provided in the yaml " +
                                "and write the output to $outDir.  The files will be named $prefix_$ampliconName_baseCounts.txt")
                .example("-bam /path/to/my.bam -prefix myPrefix -yaml /path/to/my.yaml -outDir /path/to/outputDir -before_window 4 -after_window 3",
                        "same as previous example except each amplicon guide sequence ")

        ;

        ApplicationUtil.CliResult cliResult = ApplicationUtil.parse(spec, args);
        if(cliResult.shouldExit()){
            System.exit(cliResult.getExitCode());
        }
        try {
            File outputDir = options.getOutputDir();
            if(outputDir !=null) {
                Files.createDirectories(outputDir.toPath());
            }
            List<Amplicon> ampliconList = Amplicon.parseYaml(options.getYaml());


            boolean showRefGaps=false;


            SamParser parser = SamParserFactory.create(options.getBam(),
                    SamParserFactory.Parameters.builder()
                            //ignore bai for now as there is either bug in Jillion
                            //or the logic for finding reads that overlaps the input ranges
                            //is calculated differently in bai files (I think it's
                            //a bai file index only contains reads FULLY CONTAINED
                            //in the region where as we want intersecting
                            .ignoreBai(true)
                            .build()
            );




            Map<String,Amplicon> amplicons = ampliconList.stream()
                                            .collect(Collectors.toMap(Amplicon::getName, Function.identity()));

            Map<String,Map<String, Map<String, DirectedRange>>> guideSeqHits = new HashMap<>();
            Map<String, List<Range>> ampliconRangesByReference = new HashMap<>();
            for(Amplicon amplicon : ampliconList){

                ampliconRangesByReference.computeIfAbsent(amplicon.getInfo().getChr(), k-> new ArrayList<>()).add(amplicon.getInfo().asRange());

                Map<String, DirectedRange> guideSeqMap = guideSeqHits.computeIfAbsent(amplicon.getInfo().getChr(), k-> new HashMap<>())
                        .computeIfAbsent(amplicon.getName(), k-> new HashMap<>());
                amplicon.getInfo().getGuides()
                        .stream().filter(guide-> !guide.isFailed())
                        .forEach(guide ->{
                    guideSeqMap.put(guide.seq.toString(), DirectedRange.create(
                            guide.getCoords().get("reference").asRange(),
                            guide.getCoords().get("reference").getStrand()));
                });
            }
            removedUnmappedGuideSeqs(guideSeqHits);

            if(guideSeqHits.isEmpty()){
                //nothing to do exit
                System.exit(0);
            }


            try (DataStore<PartialNucleotideSequence> referenceDataStore = createPartialReferenceDataStoreFrom(parser, ampliconList)) {


                SamRecordFilter filter = createFilterForOnlyAmplicons(ampliconList);
                SamTransformationService<PartialNucleotideSequence, PartialNucleotideSequence.PartialNucleotideSequenceBuilder> transformationService = SamTransformationService.create(parser, referenceDataStore, filter,
                        ampliconRangesByReference, false);

                for (Map.Entry<String, Map<String, Map<String, DirectedRange>>> chromEntry : guideSeqHits.entrySet()) {

                    RangeMap<ColumnInfo[]> counts = new RangeMap<>();
                    List<Range> unmergedGuideWindows = new ArrayList<>();
                    Map<Range, Range> guideToWindowGuide = new HashMap<>();
                    for (Map.Entry<String, Map<String, DirectedRange>> ampliconEntry : chromEntry.getValue().entrySet()) {
                        for (Map.Entry<String, DirectedRange> guideEntry : ampliconEntry.getValue().entrySet()) {
                            Range windowedGuide = guideEntry.getValue().asRange().toBuilder()
                                    .expandBegin(options.beforeWindow)
                                    .expandEnd(options.afterWindow)
                                    .build();
                            unmergedGuideWindows.add(windowedGuide);
                            guideToWindowGuide.put(guideEntry.getValue().asRange(), windowedGuide);
                        }
                    }
                    //need to make this effectively final
                    List<Range> guideWindows = Ranges.merge(unmergedGuideWindows);


                    MyAbstractAssemblyTransformer transformer = new MyAbstractAssemblyTransformer(guideWindows, counts);
                    //expand ranges for parsing...
                    List<Range> rangesToQuery = guideWindows.stream()
                            .map(r -> r.toBuilder()
                                    .expandBegin(200)
                                    .expandEnd(200)
                                    .build())
                            .collect(RangeCollectors.mergeRanges());
                    transformationService.transform(chromEntry.getKey(), rangesToQuery, transformer);
                    SingleThreadAdder ZERO = new SingleThreadAdder(0);

                    for (Map.Entry<String, Map<String, DirectedRange>> ampliconEntry : chromEntry.getValue().entrySet()) {
                        try (PrintWriter writer = new PrintWriter(
                                new FileOutputStream(new File(options.getOutputDir(), (options.getPrefix() == null ? "" : (options.getPrefix() + "_")) + ampliconEntry.getKey() + "_baseCounts.txt")), false)) {

                            writer.println("sample\tamplicon name\tchrom\tguide\tstrand\tref offset\tamplicon position\tguide position\tref\tmajority\tA\tC\tG\tT\tN\t-");
                            for (Map.Entry<String, DirectedRange> guideEntry : ampliconEntry.getValue().entrySet()) {
                                Range gappedReferenceRange = transformer.ungappedToGappedMap.get(guideToWindowGuide.get(guideEntry.getValue().asRange()));
                                counts.getAllThatIntersect(gappedReferenceRange, (guideRange, infos, ignored) -> {
                                    //guideRange is gapped we actually don't want that
                                    Range ungappedReferenceRange = transformer.gappedToUngappedMap.get(guideRange);
                                    long currentPosition = ungappedReferenceRange.getBegin(); // one based
                                    long shiftFromGuide;
                                    long ampliconPosition;
                                    Range ampliconRange = amplicons.get(ampliconEntry.getKey()).getInfo()
                                            .getGuides().stream()
                                            .filter(g -> g.getSeq().toString().equals(guideEntry.getKey()))
                                            .findAny().get()
                                            .getCoords().get("amplicon").asRange();
                                    if (guideEntry.getValue().getDirection() == Direction.FORWARD) {
                                        shiftFromGuide = currentPosition - guideEntry.getValue().getBegin();
                                        ampliconPosition = ampliconRange.getBegin() - shiftFromGuide;
                                    } else {
                                        shiftFromGuide = guideEntry.getValue().getEnd() - currentPosition + 1;
                                        ampliconPosition = ampliconRange.getBegin() + shiftFromGuide;
                                    }

                                    for (int i = 0; i < infos.length; i++) {
                                        if (shiftFromGuide == 0) {
                                            // we don't want to have position 0
                                            if (guideEntry.getValue().getDirection() == Direction.FORWARD) {
                                                shiftFromGuide++;
                                            } else {
                                                shiftFromGuide--;
                                            }
                                        }
                                        ColumnInfo info = infos[i];
                                        if (info.getRef().isGap() && !showRefGaps) {
                                            continue; //skip indels for now
                                        }
                                        List<String> fields = new ArrayList<>();
                                        fields.add(options.sampleName);
                                        fields.add(ampliconEntry.getKey());
                                        fields.add(chromEntry.getKey());
                                        fields.add(guideEntry.getKey());
                                        fields.add(guideEntry.getValue().getDirection() == Direction.FORWARD ? "+" : "-");
                                        fields.add(Long.toString(currentPosition++));
                                        fields.add(Long.toString(ampliconPosition));
                                        fields.add(Long.toString(shiftFromGuide));
                                        fields.add(info.getRef().toString());
                                        Optional<Nucleotide> consensus = info.getMostFrequentBase();
                                        fields.add(consensus.isPresent() ? consensus.get().toString() : "");
                                        fields.add(Long.toString(info.getTotalCounts().getOrDefault(Nucleotide.Adenine, ZERO).longValue()));
                                        fields.add(Long.toString(info.getTotalCounts().getOrDefault(Nucleotide.Cytosine, ZERO).longValue()));
                                        fields.add(Long.toString(info.getTotalCounts().getOrDefault(Nucleotide.Guanine, ZERO).longValue()));
                                        fields.add(Long.toString(info.getTotalCounts().getOrDefault(Nucleotide.Thymine, ZERO).longValue()));
                                        fields.add(Long.toString(info.getTotalCounts().getOrDefault(Nucleotide.Unknown, ZERO).longValue()));
                                        fields.add(Long.toString(info.getTotalCounts().getOrDefault(Nucleotide.Gap, ZERO).longValue()));


                                        String line = fields.stream().collect(Collectors.joining("\t"));
                                        writer.println(line);

                                        if (guideEntry.getValue().getDirection() == Direction.FORWARD) {
                                            shiftFromGuide++;
                                            ampliconPosition++;
                                        } else {
                                            shiftFromGuide--;
                                            ampliconPosition--;
                                        }
                                    }
                                });
                            }

                        }
                    }


                }

            }
        }catch(Throwable t){
            t.printStackTrace();
            //set non-zero exit code
            System.exit(1);
        }
    }

    private static void removedUnmappedGuideSeqs(Map<String, Map<String, Map<String, DirectedRange>>> guideSeqHits) {
        //some guide sequences may have failed
        //if ALL guide sequences in yaml failed for an amplicon, then don't include it
        guideSeqHits.entrySet().removeIf(entry->{

            entry.getValue().entrySet().removeIf(e2-> e2.getValue().isEmpty());
            return entry.getValue().isEmpty();
        });
    }

    private static SamRecordFilter createFilterForOnlyAmplicons(List<Amplicon> ampliconList) {
        Map<String, List<Range>> ampliconRanges = new HashMap<>();
        for(Amplicon amplicon: ampliconList){
            ampliconRanges.computeIfAbsent(amplicon.getInfo().chr, k-> new ArrayList<>())
                    .add(amplicon.getInfo().asRange());
        }
        Map<String, List<Range>> mergedAmpliconRanges = ampliconRanges.entrySet()
                .stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e-> Ranges.merge(e.getValue())));
        return  SamRecordFilter.wrap(record-> {
            if(!record.mapped()){
                return false;
            }
            List<Range> ranges = mergedAmpliconRanges.get(record.getReferenceName());
            if(ranges ==null){
                return false;
            }
            return Ranges.intersects(ranges, record.getAlignmentRange());

        });
    }

    private static class GuideSeqFinder extends AbstractSamVisitor{
            final Map<String, Set<Range>> aligned = new HashMap<>();
            //referenceName < ampliconName, List<Range>>
        Map<String, Map<String, List<Range>>> guideSeqHits = new HashMap<>();
        final Map<String, List<Range>> ampliconRangesByReference = new HashMap<>();
        final NucleotideFastaDataStore referenceDataStore;
        final Map<String,Amplicon> amplicons;

        private GuideSeqFinder(NucleotideFastaDataStore referenceDataStore, Map<String,Amplicon> amplicons) {
            this.referenceDataStore = referenceDataStore;
            this.amplicons = amplicons;
        }

        @Override
            public void visitRecord(SamVisitor.SamVisitorCallback callback, SamRecord record, VirtualFileOffset start, VirtualFileOffset end) {
                if (record.mapped()) {
                    aligned.computeIfAbsent(record.getReferenceName(), k -> new HashSet<>()).add(record.getAlignmentRange());
                }
            }

            @Override
            public void visitEnd(){

                for (Map.Entry<String, Set<Range>> entry : aligned.entrySet()) {
                    String referenceName = entry.getKey();
                    List<Range> alignedRanges = Ranges.merge(entry.getValue());

                    System.out.println(referenceName);
                    System.out.println(alignedRanges);

                    try {
                        NucleotideSequence refSeq = referenceDataStore.get(referenceName).getSequence();
                        System.out.println(refSeq.getLength());
                        for(Map.Entry<String, Amplicon> ampliconEntry: amplicons.entrySet()) {

                            for (Range r : alignedRanges) {
                                //guideSeqHits.computeIfAbsent(referenceName, k -> new ArrayList<>()).addAll(

                                List<Range> hitRanges = refSeq.findMatches(ampliconEntry.getValue().getInfo().getGuides().get(0).getSeq().toString(), r)
                                                .collect(Collectors.toList());
                                if(!hitRanges.isEmpty()){
                                    guideSeqHits.computeIfAbsent(referenceName, k-> new HashMap<>())
                                            .computeIfAbsent(ampliconEntry.getKey(), k-> new ArrayList<>()).addAll(hitRanges);
                                }

                            }
                        }
                        //compute ampliconRangesByReference Map
                        for(Map.Entry<String, Map<String, List<Range>>> refEntry : guideSeqHits.entrySet()){
                            List<Range> masterList = new ArrayList<>();
                            refEntry.getValue().values().forEach(masterList::addAll);
                            ampliconRangesByReference.put(refEntry.getKey(), masterList);
                        }


                    } catch (DataStoreException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        }

    private static class MyAbstractAssemblyTransformer extends AbstractAssemblyTransformer {
        private final List<Range> guideWindows;
        private final RangeMap<ColumnInfo[]> counts;
        INucleotideSequence<?,?> gappedReference;

        private Map<Range, Range> gappedToUngappedMap = new HashMap<>();
        private Map<Range, Range> ungappedToGappedMap = new HashMap<>();
        public MyAbstractAssemblyTransformer(List<Range> guideWindows, RangeMap<ColumnInfo[]> counts) {
            this.guideWindows = guideWindows;
            this.counts = counts;
        }

        @Override
        public void referenceOrConsensus(String id, INucleotideSequence<?,?> gappedReference, AssemblyTransformerCallback callback) {

            this.gappedReference = gappedReference;
            for(Range ungappedRange : guideWindows){
                Range gappedRange = gappedReference.toGappedRange(ungappedRange);
                gappedToUngappedMap.put(gappedRange, ungappedRange);
                ungappedToGappedMap.put(ungappedRange, gappedRange);
                ColumnInfo[] infos = new ColumnInfo[(int) gappedRange.getLength()];
                counts.put(gappedRange, infos);
                int i=0;
                int ungappedOffset=(int) ungappedRange.getBegin();
                int gappedOffset=(int) gappedRange.getBegin();
                for(Nucleotide refBase : gappedReference.trim(gappedRange)){

                    infos[i] = new ColumnInfo(refBase, gappedOffset, ungappedOffset);
                    if(!refBase.isGap()){
                        ungappedOffset++;
                    }
                    gappedOffset++;
                    i++;
                }
            }
        }


        @Override
        public void aligned(String readId, NucleotideSequence nucleotideSequence, QualitySequence qualitySequence, PositionSequence positions,
                            URI sourceFileUri, String referenceId,
                            long gappedStartOffset, Direction direction,
                            NucleotideSequence gappedSequence, ReadInfo readInfo, Object readObject) {
           Range alignmentRange = new Range.Builder(gappedSequence.getLength())
                                           .shift(gappedStartOffset)
                                            .build();

            counts.getAllThatIntersect(alignmentRange, (guideSeqRange, counts, ignored)->{
                Range intersection = guideSeqRange.intersection(alignmentRange);
                long numBasesIntoRead = intersection.getBegin() - alignmentRange.getBegin();
                NucleotideSequence readSeqOfInterest = gappedSequence.trim(new Range.Builder(intersection.getLength())
                        .shift(numBasesIntoRead)
                        .build());


                int offsetIntoGuide = (int)(intersection.getBegin() - guideSeqRange.getBegin());

                for(Nucleotide n : readSeqOfInterest){
                    counts[offsetIntoGuide++].addRead(n, direction);
                }
            });
        }


    }
}


