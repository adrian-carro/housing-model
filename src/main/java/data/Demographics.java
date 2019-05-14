package data;

import housing.Config;
import housing.Model;

import org.apache.commons.math3.distribution.EnumeratedIntegerDistribution;
import org.apache.commons.math3.random.MersenneTwister;
import utilities.BinnedDataDouble;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;

/**************************************************************************************************
 * Class to read and work with demographics data before passing it to the Demographics class. Note
 * that, throughout this class, we use "real population" to refer to actual numbers of individuals
 * while we leave the term "population" to refer to numbers of agents, i.e., numbers of households
 *
 * @author daniel, Adrian Carro
 *
 *************************************************************************************************/
public class Demographics {

    //------------------//
    //----- Fields -----//
    //------------------//

    private static Config config = Model.config; // Passes the Model's configuration parameters object to a private field
    private static MersenneTwister rand = Model.rand; // Passes the Model's random number generator to a private static field

    // Read original age distribution from file
    private static BinnedDataDouble ageDistribution = new BinnedDataDouble(config.DATA_AGE_DISTRIBUTION);

    // Transform original age distribution to a new distribution with monthly bins (linear assumption)
    private static BinnedDataDouble monthlyAgeDistribution = transformAgeDistributionToMonthly(ageDistribution);

    // Compute expected number of households for each monthly age band given a certain target population
    private static int [] expectedHouseholdsPerAgeBand = computeExpectedHouseholdsPerAgeBand(monthlyAgeDistribution,
            config.TARGET_POPULATION);

    private static int  totalRealPopulation = 0;

    /**
     * Target number of households for each region. Note that we are using Local Authority Districts as regions and that
     * we only have data on their population, not their number of households. Furthermore, we want to be able to set the
     * target total number of agents as a separate parameter. To solve this, we assume that each Local Authority
     * District contains the same fraction of the total number of households as their fraction of the total population.
     */
    private static ArrayList<Integer> targetPopulationPerRegion =
            computeTargetPopulationPerRegion(config.DATA_REAL_POPULATION_PER_REGION, config.TARGET_POPULATION);

    // Compute a probability distribution of regions (numbers) proportional to their target population
    private static EnumeratedIntegerDistribution probDistOfRegionsByPopulation = computeProbDistOfRegionsByPopulation();

    //-------------------//
    //----- Methods -----//
    //-------------------//

    /**
     * Compute an alternative age distribution with a bin per month from the original one assuming straight linear
     * behaviour between every two points
     */
    private static BinnedDataDouble transformAgeDistributionToMonthly(BinnedDataDouble ageDistribution) {
        // Declare and initialise the new monthly age distribution with the same minimum bin edge as the original age
        // distribution and one month as bin width
        BinnedDataDouble monthlyAgeDistribution = new BinnedDataDouble(ageDistribution.getSupportLowerBound(),
                1.0 / config.constants.MONTHS_IN_YEAR);
        // Declare instrumental variables to save space
        int minAge = (int)ageDistribution.getSupportLowerBound();
        int maxAge = (int)ageDistribution.getSupportUpperBound();

        // Compute original bin centers
        double [] binCenters = new double[ageDistribution.size()];
        for (int i = 0; i < ageDistribution.size(); i++) {
            binCenters[i] = minAge + ageDistribution.getBinWidth() / 2.0 + i * ageDistribution.getBinWidth();
        }

        // Compute monthly bin edges and centers
        double [] monthlyBinCenters = new double[(maxAge - minAge) * config.constants.MONTHS_IN_YEAR];
        for (int i = 0; i < (maxAge - minAge) * config.constants.MONTHS_IN_YEAR; i++) {
            monthlyBinCenters[i] = minAge + monthlyAgeDistribution.getBinWidth() / 2.0
                    + i * monthlyAgeDistribution.getBinWidth();
        }

        // Find, for each monthly bin center, in which bin of the original bin centers it falls...
        int [] whichBin = computeWhichBin(monthlyBinCenters, binCenters);
        // ...then for each case, find the corresponding slope and intercept
        double [][] slopesAndIntercepts = computeSlopesAndIntercepts(binCenters, ageDistribution);
        double [] slopes = slopesAndIntercepts[0];
        double [] intercepts = slopesAndIntercepts[1];

        // Use these slopes and intercepts to find a monthly density for each monthly center
        for (int i = 0; i < monthlyBinCenters.length; i++) {
            double density = slopes[whichBin[i]] * monthlyBinCenters[i] + intercepts[whichBin[i]];
            if (density > 0.0) {
                monthlyAgeDistribution.add(density);
            } else {
                monthlyAgeDistribution.add(0.0);
            }
        }

        // Finally, re-normalise these densities such that, when multiplied by the monthly bin width, they add up to 1
        double factor = 0.0;
        for (double density: monthlyAgeDistribution) factor += density;
        factor *= monthlyAgeDistribution.getBinWidth();
        for (int i = 0; i < monthlyAgeDistribution.size(); i++) {
            monthlyAgeDistribution.set(i, monthlyAgeDistribution.get(i) / factor);
        }

        return monthlyAgeDistribution;
    }

    /**
     * Compute slope and intercept for each of the straight lines formed by every two points of the original age
     * distribution
     */
    private static double [][] computeSlopesAndIntercepts(double [] binCenters, BinnedDataDouble ageDistribution) {
        // For each case, find the corresponding slope and intercept and add it to an array...
        double[] slopes = new double[binCenters.length + 1];
        double[] intercepts = new double[binCenters.length + 1];
        for (int i = 1; i < binCenters.length; i++) {
            slopes[i] = (ageDistribution.getBinAt(binCenters[i]) - ageDistribution.getBinAt(binCenters[i - 1]))
                    / (binCenters[i] - binCenters[i - 1]);
            intercepts[i] = ageDistribution.getBinAt(binCenters[i]) - slopes[i] * binCenters[i];
        }
        // ...including an extension of the initial and final slopes to the edges of the age distribution
        slopes[0] = slopes[1];
        intercepts[0] = intercepts[1];
        slopes[slopes.length - 1] = slopes[slopes.length - 2];
        intercepts[intercepts.length - 1] = intercepts[intercepts.length - 2];
        return new double[][]{slopes, intercepts};
    }

    /**
     * Compute the number of bin of longEdges in which each value of shortEdges falls, assigning 0 to shortEdges values
     * below the minimum longEdges, and length(longEdges) to values of shortEdges beyond the maximum longEdges
     */
    private static int [] computeWhichBin(double [] shortEdges, double [] longEdges) {
        int [] whichBin = new int[shortEdges.length];
        int i = 0;
        int j = 0;
        for (double threshold: longEdges) {
            while (j < shortEdges.length && shortEdges[j] < threshold) {
                whichBin[j] = i;
                j++;
            }
            i++;
        }
        // Short center beyond the maximum long center, receive length(longCenters) as bin number
        while (j < shortEdges.length) {
            whichBin[j] = i;
            j++;
        }
        return whichBin;
    }

    /**
     * Compute the expected number of households in each age band given a target population
     */
    private static int [] computeExpectedHouseholdsPerAgeBand(BinnedDataDouble ageDistribution,
                                                              int targetPopulation) {
        int [] expectedHouseholdsPerAgeBand = new int[ageDistribution.size()];
        for (int i = 0; i < ageDistribution.size(); i++) {
            expectedHouseholdsPerAgeBand[i] = (int)Math.round(targetPopulation * ageDistribution.get(i)
                    * ageDistribution.getBinWidth());
        }
        return expectedHouseholdsPerAgeBand;
    }

    private static EnumeratedIntegerDistribution computeProbDistOfRegionsByPopulation() {
        int [] regionNumbers = new int[targetPopulationPerRegion.size()];
        double [] probabilities = new double[targetPopulationPerRegion.size()];
        int i = 0;
        for (int targetPopulation : targetPopulationPerRegion) {
            regionNumbers[i] = i;
            probabilities[i] = (double)targetPopulation / config.TARGET_POPULATION;
            i += 1;
        }
        return new EnumeratedIntegerDistribution(rand, regionNumbers, probabilities);
    }

    /**
     * Method to compute target numbers of households for each region. It makes use of data on the actual population of
     * each region, the target total number of households in the model set by the user, and the assumption that regions
     * contain the same fraction of households as the fraction they contain of the total population.
     *
     * @param fileName String with name of file (address inside source folder)
     * @param totalTargetPopulation Integer with the total target number of households set by the user
     * @return targetPopulationPerRegion ArrayList of integers with the target number of households for each region
     */
    private static ArrayList<Integer> computeTargetPopulationPerRegion(String fileName, int totalTargetPopulation) {
        ArrayList<Integer> targetPopulationPerRegion = new ArrayList<>();
        for (Integer realPopulation: readRealPopulationPerRegion(fileName)) {
            targetPopulationPerRegion.add(totalTargetPopulation*realPopulation/totalRealPopulation);
        }
        return targetPopulationPerRegion;
    }

    /**
     * Method to read the real population of each region from a data file.
     *
     * @param fileName String with name of file (address inside source folder)
     * @return realPopulationPerRegion ArrayList of integers with the real population of each region
     */
    private static ArrayList<Integer> readRealPopulationPerRegion(String fileName) {
        int realPopulation;
        ArrayList<Integer> realPopulationPerRegion = new ArrayList<>();
        // Try-with-resources statement
        try (BufferedReader buffReader = new BufferedReader(new FileReader(fileName))) {
            String line = buffReader.readLine();
            while (line != null) {
                if (line.charAt(0) != '#') {
                    try {
                        realPopulation = Integer.parseInt(line.split(",")[1].trim());
                        realPopulationPerRegion.add(realPopulation);
                        totalRealPopulation += realPopulation;
                    } catch (NumberFormatException nfe) {
                        System.out.println("Exception " + nfe + " while trying to parse " +
                                line.split(",")[1] + " for an integer");
                        nfe.printStackTrace();
                    }
                }
                line = buffReader.readLine();
            }
        } catch (IOException ioe) {
            System.out.println("Exception " + ioe + " while trying to read file '" + fileName + "'");
            ioe.printStackTrace();
        }
        return realPopulationPerRegion;
    }

    //----- Getter/setter methods -----//

    public static ArrayList<Integer> getTargetPopulationPerRegion() { return targetPopulationPerRegion; }

    public static EnumeratedIntegerDistribution getProbDistOfRegionsByPopulation() {
        return probDistOfRegionsByPopulation;
    }

    public static int getExpectedHouseholdsForAgeBand(int i) {
        return expectedHouseholdsPerAgeBand[i];
    }

    public static double getMonthlyAgeDistributionMinimum() {
        return monthlyAgeDistribution.getSupportLowerBound();
    }

    public static double getMonthlyAgeDistributionBinWidth() {
        return monthlyAgeDistribution.getBinWidth();
    }

    public static int getMonthlyAgeDistributionSize() {
        return monthlyAgeDistribution.size();
    }
}
