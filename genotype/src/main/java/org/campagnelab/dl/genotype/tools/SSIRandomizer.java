package org.campagnelab.dl.genotype.tools;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.logging.ProgressLogger;
import it.unimi.dsi.util.XoRoShiRo128PlusRandom;
import org.apache.commons.io.FileUtils;
import org.campagnelab.dl.framework.tools.arguments.AbstractTool;
import org.campagnelab.dl.genotype.storage.SegmentReader;
import org.campagnelab.dl.genotype.storage.SegmentWriter;
import org.campagnelab.dl.varanalysis.protobuf.SegmentInformationRecords;
import org.campagnelab.goby.baseinfo.BasenameUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Random;


/**
 * A randomizer for ssi/ssip files.
 *
 * @author manuele
 */
public class SSIRandomizer extends AbstractTool<SSIRandomizerArguments> {

    static private Logger LOG = LoggerFactory.getLogger(SSIRandomizer.class);

    public static void main(String[] args) {
        SSIRandomizer tool = new SSIRandomizer();
        tool.parseArguments(args, "SSIRandomizer", tool.createArguments());
        tool.execute();
    }

    @Override
    public SSIRandomizerArguments createArguments() {
        return new SSIRandomizerArguments();
    }

    @Override
    public void execute() {
        String workingDir = new File(args().outputFile).getParent();
        if (workingDir == null) {
            workingDir = ".";
        }
        try {
            long totalRecords = 0;
            for (String filename : args().inputFiles) {
                SegmentReader source = new SegmentReader(filename);
                totalRecords += Math.min(args().readN,source.getTotalRecords());
                source.close();
            }
            int numBuckets = (int) (totalRecords / arguments.recordsPerBucket) + 1;
            new File(workingDir + "/tmp").mkdir();
            List<SegmentWriter> bucketWriters = new ObjectArrayList<>(numBuckets);
            for (int i = 0; i < numBuckets; i++) {
                bucketWriters.add(new SegmentWriter(workingDir + "/tmp/bucket" + i, arguments.chunkSizePerWriter));
            }
            SegmentWriter allWriter = new SegmentWriter(args().outputFile);
            Random rand = new XoRoShiRo128PlusRandom(args().randomSeed);
            //set up logger
            ProgressLogger pgRead = new ProgressLogger(LOG);
            pgRead.itemsName = "sites";
            pgRead.expectedUpdates = totalRecords;
            pgRead.displayFreeMemory = true;
            pgRead.start();

            //fill buckets randomly
            System.out.println("Filling " + numBuckets + " temp buckets randomly");
            for (String filename : args().inputFiles) {
                SegmentReader source = new SegmentReader(filename);
                long count=0;
                for (SegmentInformationRecords.SegmentInformation rec : source) {
                    int bucket = rand.nextInt(numBuckets);
                    bucketWriters.get(bucket).writeRecord(rec);
                    pgRead.lightUpdate();
                    count++;
                    if (count>args().readN) break;

                }
                source.close();
                System.gc();
            }

            pgRead.stop();

            System.out.println("Shuffling contents of each bucket and writing to output file");
            System.out.printf("There are %d buckets to shuffle\n", numBuckets);
            //iterate over buckets
            ProgressLogger pgTempBucket = new ProgressLogger(LOG);
            pgTempBucket.itemsName = "buckets";
            pgTempBucket.expectedUpdates = numBuckets;
            pgTempBucket.displayFreeMemory = true;
            pgTempBucket.start();
            int i = 0;
            for (SegmentWriter bucketWriter : bucketWriters) {
                bucketWriter.close();

                //put contents of bucket in a list
                SegmentReader bucketReader = new SegmentReader(workingDir + "/tmp/bucket" + i);
                List<SegmentInformationRecords.SegmentInformation> records = new ObjectArrayList<>(arguments.recordsPerBucket);
                for (SegmentInformationRecords.SegmentInformation rec : bucketReader) {
                    records.add(rec);
                }
                bucketReader.close();

                //shuffle list
                Collections.shuffle(records, rand);

                //write list to final file
                for (SegmentInformationRecords.SegmentInformation rec : records) {
                    allWriter.writeRecord(rec);
                }
                i++;
                pgTempBucket.update();
            }
            pgTempBucket.stop();
            allWriter.close();
            final String sourceFilename = args().inputFiles.get(0);
            String sourceBasename = BasenameUtils.getBasename(sourceFilename,".ssi",".ssip");
            String destBasename = BasenameUtils.getBasename(args().outputFile,".ssi",".ssip");
            FileUtils.copyFile(new File(sourceBasename + ".ssip"), new File(destBasename + ".ssip"));
            //delete temp files
            FileUtils.deleteDirectory(new File((workingDir + "/tmp")));

        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e);

        }
    }
}
