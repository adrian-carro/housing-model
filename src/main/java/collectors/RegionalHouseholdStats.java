package collectors;

import housing.Config;
import housing.Household;
import housing.Region;

/**************************************************************************************************
 * Class to collect regional household statistics
 *
 * @author Adrian Carro
 * @since 06/09/2017
 *
 *************************************************************************************************/
public class RegionalHouseholdStats extends CollectorBase {

    //------------------//
    //----- Fields -----//
    //------------------//

    // General fields
    private Config  config; // Private field to receive the Model's configuration parameters object
    private Region  region;

    // Fields for counting numbers of the different types of households and household conditions
    private int     nBTL; // Number of buy-to-let (BTL) households, i.e., households with the BTL gene (includes both active and inactive)
    private int     nActiveBTL; // Number of BTL households with, at least, one BTL property
    private int     nBTLOwnerOccupier; // Number of BTL households owning their home but without any BTL property
    private int     nBTLHomeless; // Number of homeless BTL households
    private int     nBTLBankruptcies; // Number of BTL households going bankrupt in a given time step
    private int     nNonBTLOwnerOccupier; // Number of non-BTL households owning their home
    private int     nRenting; // Number of (by definition, non-BTL) households renting their home
    private int     nNonBTLHomeless; // Number of homeless non-BTL households
    private int     nNonBTLBankruptcies; // Number of non-BTL households going bankrupt in a given time step

    // Fields for summing annualised total incomes
    private double  activeBTLAnnualisedTotalIncome;
    private double  ownerOccupierAnnualisedTotalIncome;
    private double  rentingAnnualisedTotalIncome;
    private double  homelessAnnualisedTotalIncome;

    // Other fields
    private double  sumStockYield; // Sum of stock gross rental yields of all currently occupied rental properties
    private int     nBiddersAboveExpAvSalePrice; // Number of bidders with desired housing expenditure above the exponential moving average sale price
    private int     nBiddersAboveExpAvSalePriceCounter; // Counter for the number of bidders with desired housing expenditure above the exp. moving average sale price


    //------------------------//
    //----- Constructors -----//
    //------------------------//

    /**
     * Initialises the regional household statistics collector with a reference to the region owning it
     *
     * @param region Reference to the region owning both the market and the regional collector
     */
    public RegionalHouseholdStats(Config config, Region region) {
        setActive(true);
        this.config = config;
        this.region = region;
    }

    //-------------------//
    //----- Methods -----//
    //-------------------//

    /**
     * Sets initial values for all relevant variables to enforce a controlled first measure for statistics
     */
    public void init() {
        nBTL = 0;
        nActiveBTL = 0;
        nBTLOwnerOccupier = 0;
        nBTLHomeless = 0;
        nBTLBankruptcies = 0;
        nNonBTLOwnerOccupier = 0;
        nRenting = 0;
        nNonBTLHomeless = 0;
        nNonBTLBankruptcies = 0;
        activeBTLAnnualisedTotalIncome = 0.0;
        ownerOccupierAnnualisedTotalIncome = 0.0;
        rentingAnnualisedTotalIncome = 0.0;
        homelessAnnualisedTotalIncome = 0.0;
        sumStockYield = 0.0;
        nBiddersAboveExpAvSalePrice = 0;
        nBiddersAboveExpAvSalePriceCounter = 0;
    }

    public void record() {
        // Initialise variables to sum
        nBTL = 0;
        nActiveBTL = 0;
        nBTLOwnerOccupier = 0;
        nBTLHomeless = 0;
        nBTLBankruptcies = 0;
        nNonBTLOwnerOccupier = 0;
        nRenting = 0;
        nNonBTLHomeless = 0;
        nNonBTLBankruptcies = 0;
        activeBTLAnnualisedTotalIncome = 0.0;
        ownerOccupierAnnualisedTotalIncome = 0.0;
        rentingAnnualisedTotalIncome = 0.0;
        homelessAnnualisedTotalIncome = 0.0;
        sumStockYield = 0.0;
        // Run through all households counting population in each type and summing their gross incomes
        for (Household h : region.households) {
            if (h.behaviour.isPropertyInvestor()) {
                ++nBTL;
                if (h.isBankrupt()) nBTLBankruptcies += 1;
                // Active BTL investors
                if (h.nInvestmentProperties() > 0) {
                    ++nActiveBTL;
                    activeBTLAnnualisedTotalIncome += h.getMonthlyGrossTotalIncome();
                    // Inactive BTL investors who own their house
                } else if (h.nInvestmentProperties() == 0) {
                    ++nBTLOwnerOccupier;
                    ownerOccupierAnnualisedTotalIncome += h.getMonthlyGrossTotalIncome();
                    // Inactive BTL investors in social housing
                } else {
                    ++nBTLHomeless;
                    homelessAnnualisedTotalIncome += h.getMonthlyGrossTotalIncome();
                }
            } else {
                if (h.isBankrupt()) nNonBTLBankruptcies += 1;
                // Non-BTL investors who own their house
                if (h.isHomeowner()) {
                    ++nNonBTLOwnerOccupier;
                    ownerOccupierAnnualisedTotalIncome += h.getMonthlyGrossTotalIncome();
                    // Non-BTL investors renting
                } else if (h.isRenting()) {
                    ++nRenting;
                    rentingAnnualisedTotalIncome += h.getMonthlyGrossTotalIncome();
                    if (region.regionalHousingMarketStats.getExpAvSalePriceForQuality(h.getHome().getQuality()) > 0) {
                        sumStockYield += h.getHousePayments().get(h.getHome()).monthlyPayment
                                *config.constants.MONTHS_IN_YEAR
                                /region.regionalHousingMarketStats.getExpAvSalePriceForQuality(h.getHome().getQuality());
                    }
                // Non-BTL investors in social housing
                } else if (h.isInSocialHousing()) {
                    // TODO: Once numbers are checked, this "else if" can be replaced by an "else"
                    ++nNonBTLHomeless;
                    homelessAnnualisedTotalIncome += h.getMonthlyGrossTotalIncome();
                }
            }
        }
        // Annualise monthly income data
        activeBTLAnnualisedTotalIncome *= config.constants.MONTHS_IN_YEAR;
        ownerOccupierAnnualisedTotalIncome *= config.constants.MONTHS_IN_YEAR;
        rentingAnnualisedTotalIncome *= config.constants.MONTHS_IN_YEAR;
        homelessAnnualisedTotalIncome *= config.constants.MONTHS_IN_YEAR;
        // Pass number of bidders above the exponential moving average sale price to persistent variable and
        // re-initialise to zero the counter
        nBiddersAboveExpAvSalePrice = nBiddersAboveExpAvSalePriceCounter;
        nBiddersAboveExpAvSalePriceCounter = 0;
    }

    /**
     * Count number of bidders with desired expenditures above the (minimum quality, q=0) exponential moving average
     * sale price
     */
    public void countBiddersAboveExpAvSalePrice(double price) {
        if (price >= region.regionalHousingMarketStats.getExpAvSalePriceForQuality(0)) {
            nBiddersAboveExpAvSalePriceCounter++;
        }
    }

    //----- Getter/setter methods -----//

    // Getters for numbers of households variables
    public int getnBTL() { return nBTL; }
    public int getnActiveBTL() { return nActiveBTL; }
    public int getnBTLOwnerOccupier() { return nBTLOwnerOccupier; }
    public int getnBTLHomeless() { return nBTLHomeless; }
    public int getnBTLBankruptcies() { return nBTLBankruptcies; }
    public int getnNonBTLOwnerOccupier() { return nNonBTLOwnerOccupier; }
    public int getnRenting() { return nRenting; }
    public int getnNonBTLHomeless() { return nNonBTLHomeless; }
    public int getnNonBTLBankruptcies() { return nNonBTLBankruptcies; }
    public int getnOwnerOccupier() { return nBTLOwnerOccupier + nNonBTLOwnerOccupier; }
    public int getnHomeless() { return nBTLHomeless + nNonBTLHomeless; }
    public int getnNonOwner() { return nRenting + getnHomeless(); }

    // Getters for annualised income variables
    public double getActiveBTLAnnualisedTotalIncome() { return activeBTLAnnualisedTotalIncome; }
    public double getOwnerOccupierAnnualisedTotalIncome() { return ownerOccupierAnnualisedTotalIncome; }
    public double getRentingAnnualisedTotalIncome() { return rentingAnnualisedTotalIncome; }
    public double getHomelessAnnualisedTotalIncome() { return homelessAnnualisedTotalIncome; }
    public double getNonOwnerAnnualisedTotalIncome() {
        return rentingAnnualisedTotalIncome + homelessAnnualisedTotalIncome;
    }

    // Getters for yield variables
    public double getSumStockYield() { return sumStockYield; }
    public double getAvStockYield() {
        if(nRenting > 0) {
            return sumStockYield/nRenting;
        } else {
            return 0.0;
        }
    }

    // Getters for other variables...
    // ... number of empty houses (total number of houses minus number of non-homeless households)
    public int getnEmptyHouses() {
        return region.getHousingStock() + nBTLHomeless + nNonBTLHomeless - region.households.size();
    }
    // ... proportion of housing stock owned by buy-to-let investors (all rental properties, plus all empty houses not
    // owned by the construction sector)
    public double getBTLStockFraction() {
        return ((double)(getnEmptyHouses() - region.regionalHousingMarketStats.getnUnsoldNewBuild()
                + nRenting))/region.getHousingStock();
    }
    // ... number of bidders with desired housing expenditure above the exponential moving average sale price
    public int getnBiddersAboveExpAvSalePrice() {
        return nBiddersAboveExpAvSalePrice;
    }

//    // Array with ages of all households
//    public double [] getAgeDistribution() {
//        double [] result = new double[region.households.size()];
//        int i = 0;
//        for(Household h : region.households) {
//            result[i] = h.getAge();
//            ++i;
//        }
//        return(result);
//    }
//
//    // Array with ages of renters and households in social housing
//    public double [] getNonOwnerAges() {
//        double [] result = new double[getnNonOwner()];
//        int i = 0;
//        for(Household h : region.households) {
//            if(!h.isHomeowner() && i < getnNonOwner()) {
//                result[i++] = h.getAge();
//            }
//        }
//        while(i < getnNonOwner()) {
//            result[i++] = 0.0;
//        }
//        return(result);
//    }
//
//    // Array with ages of owner-occupiers
//    public double [] getOwnerOccupierAges() {
//        double [] result = new double[getnNonOwner()];
//        int i = 0;
//        for(Household h : region.households) {
//            if(!h.isHomeowner() && i < getnNonOwner()) {
//                result[i] = h.getAge();
//                ++i;
//            }
//        }
//        while(i < getnNonOwner()) {
//            result[i++] = 0.0;
//        }
//        return(result);
//    }
//
//    // Distribution of the number of properties owned by BTL investors
//    public double [] getBTLNProperties() {
//        if(isActive() && nBTL > 0) {
//            double [] result = new double[(int)nBTL];
//            int i = 0;
//            for(Household h : region.households) {
//                if(h.behaviour.isPropertyInvestor() && i<nBTL) {
//                    result[i] = h.nInvestmentProperties();
//                    ++i;
//                }
//            }
//            return(result);
//        }
//        return null;
//    }
//
//    public double [] getLogIncomes() {
//        double [] result = new double[region.households.size()];
//        int i = 0;
//        for(Household h : region.households) {
//            result[i++] = Math.log(h.getAnnualGrossEmploymentIncome());
//        }
//        return(result);
//    }
//
//    public double [] getLogBankBalances() {
//        double [] result = new double[region.households.size()];
//        int i = 0;
//        for(Household h : region.households) {
//            result[i++] = Math.log(Math.max(0.0, h.getBankBalance()));
//        }
//        return(result);
//    }
}
