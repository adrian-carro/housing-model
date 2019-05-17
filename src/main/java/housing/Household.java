package housing;

import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;

import org.apache.commons.math3.random.MersenneTwister;

/**************************************************************************************************
 * This represents a household who receives an income, consumes, saves and can buy, sell, let, and
 * invest in houses.
 *
 * @author daniel, davidrpugh, Adrian Carro
 *
 *************************************************************************************************/

public class Household implements IHouseOwner {

    //------------------//
    //----- Fields -----//
    //------------------//

    private static int          id_pool;

    public int                  id; // Only used for identifying households within the class TransactionRecorder
    public HouseholdBehaviour   behaviour; // Behavioural plugin

    double                      incomePercentile; // Fixed for the whole lifetime of the household

    private Geography                       geography;
    private Region                          jobRegion;
    private Region                          homeRegion;
    private House                           home;
    private Map<House, PaymentAgreement>    housePayments = new TreeMap<>(); // Houses owned and their payment agreements
    private Map<House, RentalAgreement>     rentalContracts = new TreeMap<>(); // Houses rented out by this landlord and their payment agreements
    private Config                          config; // Private field to receive the Model's configuration parameters object
    private MersenneTwister                 rand; // Private field to receive the Model's random number generator
    private double                          age; // Age of the household representative person
    private double                          bankBalance;
    private double                          annualGrossEmploymentIncome;
    private double                          monthlyGrossEmploymentIncome;
    private boolean                         isFirstTimeBuyer;
    private boolean                         isBankrupt;

    //------------------------//
    //----- Constructors -----//
    //------------------------//

    /**
     * Initialises behaviour (determine whether the household will be a BTL investor). Households start off in social
     * housing and with their "desired bank balance" in the bank
     */
    public Household(Config config, MersenneTwister rand, double age, Geography geography,
                     Region jobRegion) {
        this.config = config;
        this.rand = rand;
        this.age = age;
        this.geography = geography;
        this.jobRegion = jobRegion;
        homeRegion = jobRegion; // Households are initially created at the region where they have a job
        home = null;
        isFirstTimeBuyer = true;
        isBankrupt = false;
        id = ++id_pool;
        incomePercentile = this.rand.nextDouble();
        behaviour = new HouseholdBehaviour(this.geography, incomePercentile);
        // Find initial values for the annual and monthly gross employment income
        annualGrossEmploymentIncome = data.EmploymentIncome.getAnnualGrossEmploymentIncome(age, incomePercentile);
        monthlyGrossEmploymentIncome = annualGrossEmploymentIncome/config.constants.MONTHS_IN_YEAR;
        bankBalance = data.Wealth.getDesiredBankBalance(getAnnualGrossTotalIncome(), behaviour.getPropensityToSave()); // Desired bank balance is used as initial value for actual bank balance
    }

    //-------------------//
    //----- Methods -----//
    //-------------------//

    //----- General methods -----//

    /**
     * Main simulation step for each household. They age, receive employment and other forms of income, make their rent
     * or mortgage payments, perform an essential consumption, make non-essential consumption decisions, manage their
     * owned properties, and make their housing decisions depending on their current housing state:
     * - Buy or rent if in social housing
     * - Sell house if owner-occupier
     * - Buy/sell/rent out properties if BTL investor
     */
    public void step() {
        isBankrupt = false; // Delete bankruptcies from previous time step
        // Update annual and monthly gross employment income
        annualGrossEmploymentIncome = data.EmploymentIncome.getAnnualGrossEmploymentIncome(age, incomePercentile);
        monthlyGrossEmploymentIncome = annualGrossEmploymentIncome/config.constants.MONTHS_IN_YEAR;
        // Add monthly disposable income (net total income minus essential consumption and housing expenses) to bank balance
        bankBalance += getMonthlyDisposableIncome();
        // Consume according to gross annual income and capped by current bank balance (after disposable income has been added)
        bankBalance -= behaviour.getDesiredConsumption(bankBalance, getAnnualGrossTotalIncome());
        // Deal with bankruptcies
        // TODO: Improve bankruptcy procedures (currently, simple cash injection), such as terminating contracts!
        if (bankBalance < 0.0) {
            bankBalance = 1.0;
            isBankrupt = true;
        }
        // Manage owned properties and close debts on previously owned properties. To this end, first, create an
        // iterator over the house-paymentAgreement pairs at the household's housePayments object
        Iterator<Entry<House, PaymentAgreement>> paymentIt = housePayments.entrySet().iterator();
        Entry<House, PaymentAgreement> entry;
        House h;
        PaymentAgreement payment;
        // Iterate over these house-paymentAgreement pairs...
        while (paymentIt.hasNext()) {
            entry = paymentIt.next();
            h = entry.getKey();
            payment = entry.getValue();
            // ...if the household is the owner of the house, then manage it
            if (h.owner == this) {
                manageHouse(h);
            // ...otherwise, if the household is not the owner nor the resident, then it is an old debt due to
            // the household's inability to pay the remaining principal off after selling a property...
            } else if (h.resident != this) {
                MortgageAgreement mortgage = (MortgageAgreement) payment;
                // ...remove this type of houses from payments as soon as the household pays the debt off
                if ((payment.nPayments == 0) & (mortgage.principal == 0.0)) {
                    paymentIt.remove();
                }
            }
        }
        // Make housing decisions depending on current housing state
        if (isInSocialHousing()) {
            bidForAHome(); // When BTL households are born, they enter here the first time and until they manage to buy a home!
        } else if (isRenting()) {
            if (housePayments.get(home).nPayments == 0) { // End of rental period for this tenant
                endTenancy();
                bidForAHome();
            }
        } else if (behaviour.isPropertyInvestor()) { // Only BTL investors who already own a home enter here
            Region chosenInvestmentRegion = behaviour.decideWhereToBuyInvestmentProperty(this);
            if (chosenInvestmentRegion != null) {
                chosenInvestmentRegion.houseSaleMarket.BTLbid(this,
                        behaviour.btlPurchaseBid(this, chosenInvestmentRegion));
            }
            // TODO: Need to call here to an equivalent to the old countBTLBidsAboveExpAvSalePrice(), not implemented yet
        } else if (!isHomeowner()){
            System.out.println("Strange: this household is not a type I recognize");
        }
    }

    /**
     * Subtracts the essential necessary consumption, housing expenses (mortgage and rental payments), and commuting
     * fees from the net total income (employment income plus property income minus taxes)
     */
    private double getMonthlyDisposableIncome() {
        // Start with net monthly income
        double monthlyDisposableIncome = getMonthlyNetTotalIncome();
        // Subtract essential, necessary consumption
        // TODO: ESSENTIAL_CONSUMPTION_FRACTION is not explained in the paper, all support is said to be consumed
        monthlyDisposableIncome -= config.ESSENTIAL_CONSUMPTION_FRACTION*config.GOVERNMENT_MONTHLY_INCOME_SUPPORT;
        // Subtract housing consumption
        for(PaymentAgreement payment: housePayments.values()) {
            monthlyDisposableIncome -= payment.makeMonthlyPayment();
        }
        // If the household has a home (whether owned or rented), subtract commuting consumption (monthly fee, not cost)
        if (home != null) monthlyDisposableIncome -= getMonthlyCommutingFee(home.region);
        return monthlyDisposableIncome;
    }

    /**
     * Subtracts the monthly aliquot part of all due taxes from the monthly gross total income. Note that only income
     * tax on employment and rental income and national insurance contributions are implemented (no capital gains tax)!
     */
    private double getMonthlyNetTotalIncome() {
        return getMonthlyGrossTotalIncome()
                - (Model.government.incomeTaxDue(getAnnualGrossTotalIncome() - getAnnualFinanceCosts())  // Income tax (with finance costs tax relief)
                + Model.government.class1NICsDue(annualGrossEmploymentIncome))  // National insurance contributions
                /config.constants.MONTHS_IN_YEAR;
    }

    /**
     * For the purpose of affordability checks for non-BTL households, a monthly net employment income is needed. This
     * makes sure that no rental income is accounted for, which non-BTL households can temporarily receive as a result
     * of temporarily renting out inherited properties while they manage to sell them. Thus, this subtracts the monthly
     * aliquot part of all due taxes (without any finance cost relief) from the monthly gross employment income
     * (ignoring any rental income). Note that only income tax on employment income and national insurance contributions
     * are implemented (no capital gains tax)!
     */
    double getMonthlyNetEmploymentIncome() {
        return getMonthlyGrossEmploymentIncome()
                - (Model.government.incomeTaxDue(annualGrossEmploymentIncome)  // Income tax
                + Model.government.class1NICsDue(annualGrossEmploymentIncome))  // National insurance contributions
                /config.constants.MONTHS_IN_YEAR;
    }

    /**
     * Adds up all interests paid on buy-to-let properties currently rented by this household, for the purpose of
     * obtaining tax relief on these costs. Note that this algorithm assumes buy-to-let investors always have interest
     * only mortgages, and that non BTL households inheriting properties never inherit any debt on these properties
     */
    private double getAnnualFinanceCosts() {
        double financeCosts = 0.0;
        for (Map.Entry<House, PaymentAgreement> entry : housePayments.entrySet()) {
            House house = entry.getKey();
            PaymentAgreement payment = entry.getValue();
            if (payment instanceof MortgageAgreement && house.owner == this && payment.nextPayment() != 0.0
                    && house.resident != null && house.resident.getHousePayments().get(house).nextPayment() != 0.0) {
                financeCosts += payment.nextPayment();
            }
        }
        return financeCosts*config.constants.MONTHS_IN_YEAR;
    }

    /**
     * Annualised gross total income, i.e., both employment and rental income
     */
    double getAnnualGrossTotalIncome() { return getMonthlyGrossTotalIncome()*config.constants.MONTHS_IN_YEAR; }

    /**
     * Adds up all sources of (gross) income on a monthly basis, i.e., both employment and rental income
     */
    public double getMonthlyGrossTotalIncome() { return monthlyGrossEmploymentIncome + getMonthlyGrossRentalIncome(); }

    /**
     * Adds up this month's rental income from all currently owned and rented properties
     */
    public double getMonthlyGrossRentalIncome() {
        double monthlyGrossRentalIncome = 0.0;
        for(RentalAgreement rentalAgreement: rentalContracts.values()) {
            monthlyGrossRentalIncome += rentalAgreement.nextPayment();
        }
        return monthlyGrossRentalIncome;
    }

    //----- Methods for house owners -----//

    /**
     * Decide what to do with a house owned by the household:
     * - if the household lives in the house, decide whether to sell it or not
     * - if the house is up for sale, rethink its offer price, and possibly take it out of the sales market if price is
     *   below mortgage debt
     * - if the house is up for rent, rethink the rent demanded
     *
     * @param house A house owned by the household
     */
    private void manageHouse(House house) {
        // If house is for sale (on sale market)...
        HouseOfferRecord forSale = house.getSaleRecord();
        if (forSale != null) {
            // ...and it has not just been inherited...
            if (Model.getTime() > forSale.gettInitialListing()) {
                // ...then update its price, if the new price is above the mortgage debt on this house
                double newPrice = behaviour.rethinkHouseSalePrice(forSale);
                if (newPrice > mortgageFor(house).principal) {
                    house.region.houseSaleMarket.updateOffer(forSale, newPrice);
                // ...otherwise, remove the offer from the sale market (note that investment properties will continue to be rented out)
                } else {
                    house.region.houseSaleMarket.removeOffer(forSale);
                }
            }
        // Otherwise, if the house is not currently for sale, decide whether to sell it or not
        } else if (decideToSellHouse(house)) putHouseForSale(house);

        // If house is for rent (on rental market), and it has not just been inherited...
        HouseOfferRecord forRent = house.getRentalRecord();
        if (forRent != null && Model.getTime() > forRent.gettInitialListing()) {
            // ...then update its price
            double newPrice = behaviour.rethinkBuyToLetRent(forRent);
            house.region.houseRentalMarket.updateOffer(forRent, newPrice);
        }        
    }

    /******************************************************
     * Having decided to sell house h, decide its initial sale price and put it up in the market.
     *
     * @param h the house being sold
     ******************************************************/
    private void putHouseForSale(House h) {
        double principal;
        MortgageAgreement mortgage = mortgageFor(h);
        if(mortgage != null) {
            principal = mortgage.principal;
        } else {
            principal = 0.0;
        }
        if (h == home) {
            h.getRegion().houseSaleMarket.offer(h, behaviour.getInitialSalePrice(h.getRegion(), h.getQuality(), principal), false);
        } else {
            h.getRegion().houseSaleMarket.offer(h, behaviour.getInitialSalePrice(h.getRegion(), h.getQuality(), principal), true);
        }
    }

    /////////////////////////////////////////////////////////
    // Houseowner interface
    /////////////////////////////////////////////////////////

    /********************************************************
     * Do all the stuff necessary when this household
     * buys a house:
     * Give notice to landlord if renting,
     * Get loan from mortgage-lender,
     * Pay for house,
     * Put house on rental market if buy-to-let and no tenant.
     ********************************************************/
    void completeHousePurchase(HouseOfferRecord sale) {
        if(isRenting()) { // give immediate notice to landlord and move out
            if(sale.getHouse().resident != null) System.out.println("Strange: my new house has someone in it!");
            if(home == sale.getHouse()) {
                System.out.println("Strange: I've just bought a house I'm renting out");
            } else {
                endTenancy();
            }
        }
        MortgageAgreement mortgage = Model.bank.requestLoan(this, sale.getPrice(),
                behaviour.decideDownPayment(this,sale.getPrice()), home == null, sale.getHouse());
        if(mortgage == null) {
            // TODO: need to either provide a way for house sales to fall through or to ensure that pre-approvals are always satisfiable
            System.out.println("Can't afford to buy house: strange");
            System.out.println("Bank balance is "+bankBalance);
            System.out.println("Annual income is "+ monthlyGrossEmploymentIncome *config.constants.MONTHS_IN_YEAR);
            if(isRenting()) System.out.println("Is renting");
            if(isHomeowner()) System.out.println("Is homeowner");
            if(isInSocialHousing()) System.out.println("Is homeless");
            if(isFirstTimeBuyer()) System.out.println("Is firsttimebuyer");
            if(behaviour.isPropertyInvestor()) System.out.println("Is investor");
            System.out.println("House owner = "+ sale.getHouse().owner);
            System.out.println("me = "+this);
        } else {
            bankBalance -= mortgage.downPayment;
            housePayments.put(sale.getHouse(), mortgage);
            // If household doesn't have a home, then it moves in to the new house
            if (home == null) {
                // If new home is in a region different from the current home region...
                if (sale.getHouse().region != homeRegion) {
                    // ...then the household must first move to the new region...
                    homeRegion.households.remove(this);
                    homeRegion = sale.getHouse().region;
                    homeRegion.households.add(this);
                }
                // ...and then move in to the house
                home = sale.getHouse();
                sale.getHouse().resident = this;
            } else if (sale.getHouse().resident == null) { // put empty buy-to-let house on rental market
                sale.getHouse().region.houseRentalMarket.offer(sale.getHouse(),
                        behaviour.buyToLetRent(sale.getHouse().getQuality(), sale.getHouse().region), false);
            } else {
                System.out.println("Strange: Bought a home with a resident");
            }
            isFirstTimeBuyer = false;
        }
    }

    /********************************************************
     * Do all stuff necessary when this household sells a house
     ********************************************************/
    public void completeHouseSale(HouseOfferRecord sale) {
        // First, receive money from sale
        bankBalance += sale.getPrice();
        // Second, find mortgage object and pay off as much outstanding debt as possible given bank balance
        MortgageAgreement mortgage = mortgageFor(sale.getHouse());
        bankBalance -= mortgage.payoff(bankBalance);
        // Third, if there is no more outstanding debt, remove the house from the household's housePayments object
        if (mortgage.nPayments == 0) {
            housePayments.remove(sale.getHouse());
            // TODO: Warning, if bankBalance is not enough to pay mortgage back, then the house stays in housePayments,
            // TODO: consequences to be checked. Looking forward, properties and payment agreements should be kept apart
        }
        // Fourth, if the house is still being offered on the rental market, withdraw the offer
        if (sale.getHouse().isOnRentalMarket()) {
            sale.getHouse().region.houseRentalMarket.removeOffer(sale.getHouse().getRentalRecord());
        }
        // Fifth, if the house is the household's home, then the household moves out and becomes temporarily homeless...
        if (sale.getHouse() == home) {
            home.resident = null;
            home = null;
        // ...otherwise, if the house has a resident, it must be a renter, who must get evicted, also the rental income
        // corresponding to this tenancy must be subtracted from the owner's monthly rental income
        } else if (sale.getHouse().resident != null) {
            rentalContracts.remove(sale.getHouse());
            sale.getHouse().resident.getEvicted();
        }
    }
    
    /**
     * A BTL investor receives this message when a tenant moves out of one of its buy-to-let houses. The household
     * simply puts the house back on the rental market.
     */
    @Override
    public void endOfLettingAgreement(House h, PaymentAgreement contract) {
        // TODO: Not sure if these checks are really needed here
        // Check that this household is the owner of the corresponding house
        if(!housePayments.containsKey(h)) {
            System.out.println("Strange: I don't own this house in endOfLettingAgreement");
        }
        // Check that the house is not currently being already offered in the rental market
        if(h.isOnRentalMarket()) System.out.println("Strange: got endOfLettingAgreement on house on rental market");
        // Remove the old rental contract from the landlord's list of rental contracts
        rentalContracts.remove(h);
        // Put house back on rental market
        h.region.houseRentalMarket.offer(h, behaviour.buyToLetRent(h.getQuality(), h.region), false);
    }

    /**********************************************************
     * This household moves out of current rented accommodation
     * and becomes homeless (possibly temporarily). Move out,
     * inform landlord and delete rental agreement.
     **********************************************************/
    private void endTenancy() {
        home.resident = null;
        home.owner.endOfLettingAgreement(home, housePayments.get(home));
        housePayments.remove(home);
        home = null;
    }
    
    /*** Landlord has told this household to get out: leave without informing landlord */
    private void getEvicted() {
        if(home == null) {
            System.out.println("Strange: got evicted but I'm homeless");            
        }
        if(home.owner == this) {
            System.out.println("Strange: got evicted from a home I own");
        }
        housePayments.remove(home);
        home.resident = null;
        home = null;        
    }

    /**
     * Do everything necessary when this household moves in to rented accommodation, such as setting up a regular
     * payment contract.
     *
     * @return The rental agreement, for passing it to the landlord
     */
    RentalAgreement completeHouseRental(HouseOfferRecord sale) {
        // Check if renter same as owner, if renter already has a home and if the house is already occupied
        if (sale.getHouse().owner == this) System.out.println("Strange: I'm trying to rent a house I own!");
        if (home != null) System.out.println("Strange: I'm renting a house but not homeless");
        if (sale.getHouse().resident != null) System.out.println("Strange: tenant moving into an occupied house");
        // Create a new rental agreement with the agreed price and with a random length between a minimum and a maximum
        RentalAgreement rent = new RentalAgreement();
        rent.monthlyPayment = sale.getPrice();
        rent.nPayments = config.TENANCY_LENGTH_AVERAGE + rand.nextInt(2*config.TENANCY_LENGTH_EPSILON + 1)
                - config.TENANCY_LENGTH_EPSILON;
        // Add the rental agreement to the house payments object of the tenant household
        housePayments.put(sale.getHouse(), rent);
        // If the tenant's new home is in a region different from its current home region...
        if (sale.getHouse().region != homeRegion) {
            // ...then first move the household to the new region...
            homeRegion.households.remove(this);
            homeRegion = sale.getHouse().region;
            homeRegion.households.add(this);
        }
        // ...and then set the house as the tenant's home and the tenant as the house's resident
        home = sale.getHouse();
        sale.getHouse().resident = this;
        // Return the rental agreement for passing it to the landlord
        return rent;
    }

    /**
     * Decide whether to bid on the house sale market or the rental market and where. This is an "intensity of choice"
     * decision (sigma function) on the cost of owning in the optimal region for this household (there where it can
     * afford the highest possible quality taking into account commuting costs) compared to the cost of renting in the
     * cheapest region for that same quality for this household (there where the price of this quality is the cheapest
     * taking into account commuting costs), with COST_OF_RENTING being an intrinsic psychological cost of not owning.
     * Note the use of max to refer to maximum quality in a given region while optimal refers to the maximum quality
     * among all regions.
     */
    private void bidForAHome() {
        // Declare variables
        RegionQualityPriceContainer optimalOptionForBuying;
        RegionQualityPriceContainer optimalOptionForRenting;
        // Find optimal option for buying (region where the household could afford the highest quality band, taking into
        // account commuting costs, among all possible regions)
        optimalOptionForBuying = behaviour.findOptimalPurchaseRegion(this);
        // If household is a potential buy-to-let investor, then always buy...
        if (behaviour.isPropertyInvestor()) {
            // ...if household cannot afford minimum quality anywhere (optimal option for buying is null), then it
            // chooses to bid in the region with cheapest minimum quality
            if (optimalOptionForBuying == null) {
                optimalOptionForBuying = behaviour.findCheapestPurchaseRegion(this);
            }
            // ...bid in the house sale market for the capped desired price
            optimalOptionForBuying.getRegion().houseSaleMarket.bid(this,
                    optimalOptionForBuying.getDesiredPrice());
        // Otherwise, for normal households...
        } else {
            // ...if household cannot afford minimum quality anywhere (optimal option for buying is null), then it tries
            // to find the optimal rental region (where it can afford the highest quality)
            if (optimalOptionForBuying == null) {
                optimalOptionForRenting = behaviour.findOptimalRentalRegion(this);
                // ...if household cannot afford to rent minimum quality anywhere (optimal option for renting is null),
                // then it chooses to bid for rental in the cheapest region for renting (where the minimum quality has
                // the minimum price)
                if (optimalOptionForRenting == null) {
                    optimalOptionForRenting = behaviour.findCheapestRentalRegion(this);
                }
                // ...bid in the house rental market for the desired rent price
                optimalOptionForRenting.getRegion().houseRentalMarket.bid(this,
                        optimalOptionForRenting.getDesiredPrice());
            // ...otherwise, if the normal household can afford to buy somewhere...
            } else {
                // ...then find the region where the same quality has the cheapest rental cost (including commuting)
                optimalOptionForRenting =
                        behaviour.findCheapestRentalRegionForQuality(optimalOptionForBuying.getQuality(), this);
                // ...and decide between the purchase and the rental options
                if (behaviour.decideRentOrPurchase(optimalOptionForBuying, optimalOptionForRenting, this)) {
                    // ...if buying, bid in the house sale market for the capped desired price
                    optimalOptionForBuying.getRegion().houseSaleMarket.bid(this,
                            optimalOptionForBuying.getDesiredPrice());
                } else {
                    // ...if renting, bid in the house rental market for the desired rent price
                    optimalOptionForRenting.getRegion().houseRentalMarket.bid(this,
                            optimalOptionForRenting.getDesiredPrice());
                }
            }
        }
        // TODO: Need to call here to an equivalent to the old countNonBTLBidsAboveExpAvSalePrice(), not implemented yet
    }

    /********************************************************
     * Decide whether to sell ones own house.
     ********************************************************/
    private boolean decideToSellHouse(House h) {
        if(h == home) {
            return(behaviour.decideToSellHome());
        } else {
            return(behaviour.decideToSellInvestmentProperty(h, this));
        }
    }

    /***
     * Do stuff necessary when BTL investor lets out a rental
     * property
     */
    @Override
    public void completeHouseLet(HouseOfferRecord sale, RentalAgreement rentalAgreement) {
        rentalContracts.put(sale.getHouse(), rentalAgreement);
    }

    /**
     * Find the monthly commuting cost for this household: monthly commuting time times value of time, plus monthly
     * commuting fee
     */
    public double getMonthlyCommutingCost(Region region) {
        return 2.0 * (geography.getCommutingTimeBetween(jobRegion, region) * getTimeValue()
                + geography.getCommutingFeeBetween(jobRegion, region))
                * config.constants.WORKING_DAYS_IN_MONTH;
    }

    /**
     * Find the value (in GBP) of an hour of time for this household
     */
    private double getTimeValue(){
        return monthlyGrossEmploymentIncome / (config.constants.WORKING_DAYS_IN_MONTH
                * config.constants.WORKING_HOURS_IN_DAY);
    }

    /**
     * Find the monthly commuting fee for this household
     */
    public double getMonthlyCommutingFee(Region region) {
        return 2.0 * geography.getCommutingFeeBetween(jobRegion, region) * config.constants.WORKING_DAYS_IN_MONTH;
    }

    /////////////////////////////////////////////////////////
    // Inheritance behaviour
    /////////////////////////////////////////////////////////

    /**
     * Implement inheritance: upon death, transfer all wealth to the previously selected household.
     *
     * Take all houses off the markets, evict any tenants, pay off mortgages, and give property and remaining
     * bank balance to the beneficiary.
     * @param beneficiary The household that will inherit the wealth
     */
    void transferAllWealthTo(Household beneficiary) {
        // Check if beneficiary is the same as the deceased household
        if (beneficiary == this) { // TODO: I don't think this check is really necessary
            System.out.println("Strange: I'm transferring all my wealth to myself");
            System.exit(0);
        }
        // Create an iterator over the house-paymentAgreement pairs at the deceased household's housePayments object
        Iterator<Entry<House, PaymentAgreement>> paymentIt = housePayments.entrySet().iterator();
        Entry<House, PaymentAgreement> entry;
        House h;
        PaymentAgreement payment;
        // Iterate over these house-paymentAgreement pairs
        while (paymentIt.hasNext()) {
            entry = paymentIt.next();
            h = entry.getKey();
            payment = entry.getValue();
            // If the deceased household owns the house, then...
            if (h.owner == this) {
                // ...first, withdraw the house from any market where it is currently being offered
                if (h.isOnRentalMarket()) h.region.houseRentalMarket.removeOffer(h.getRentalRecord());
                if (h.isOnMarket()) h.region.houseSaleMarket.removeOffer(h.getSaleRecord());
                // ...then, if there is a resident in the house...
                if (h.resident != null) {
                    // ...and this resident is different from the deceased household, then this resident must be a
                    // tenant, who must get evicted
                    if (h.resident != this) {
                        h.resident.getEvicted(); // TODO: Explain in paper that renters always get evicted, not just if heir needs the house
                    // ...otherwise, if the resident is the deceased household, remove it from the house
                    } else {
                        h.resident = null;
                    }
                }
                // ...finally, transfer the property to the beneficiary household
                beneficiary.inheritHouse(h, ((MortgageAgreement) payment).purchasePrice);
            // Otherwise, if the deceased household does not own the house but it is living in it, then it must have
            // been renting it: end the letting agreement
            } else if (h == home) {
                h.resident = null;
                home = null;
                h.owner.endOfLettingAgreement(h, housePayments.get(h));
            }
            // If payment agreement is a mortgage, then try to pay off as much as possible from the deceased household's bank balance
            if (payment instanceof MortgageAgreement) {
                bankBalance -= ((MortgageAgreement) payment).payoff();
            }
            // Remove the house-paymentAgreement entry from the deceased household's housePayments object
            paymentIt.remove(); // TODO: Not sure this is necessary. Note, though, that this implies erasing all outstanding debt
        }
        // Finally, transfer all remaining liquid wealth to the beneficiary household
        beneficiary.bankBalance += Math.max(0.0, bankBalance);
    }
    
    /**
     * Inherit a house.
     *
     * Write off the mortgage for the house. Move into the house if renting or in social housing.
     * 
     * @param h House to inherit
     */
    private void inheritHouse(House h, double oldPurchasePrice) {
        // Create a null (zero payments) mortgage
        MortgageAgreement nullMortgage = new MortgageAgreement(this,false);
        nullMortgage.nPayments = 0;
        nullMortgage.downPayment = 0.0;
        nullMortgage.monthlyInterestRate = 0.0;
        nullMortgage.monthlyPayment = 0.0;
        nullMortgage.principal = 0.0; // If changed, trigger to re-try selling must be added to manageHouse, otherwise non-BTL households could keep BTL properties indefinitely
        nullMortgage.purchasePrice = oldPurchasePrice;
        // Become the owner of the inherited house and include it in my housePayments list (with a null mortgage)
        housePayments.put(h, nullMortgage);
        h.owner = this;
        // Check for residents in the inherited house
        if (h.resident != null) {
            System.out.println("Strange: inheriting a house with a resident");
            System.exit(0);
        }
        // If renting or homeless, move into the inherited house
        if (!isHomeowner()) {
            // If renting, first cancel my current tenancy
            if (isRenting()) {
                endTenancy();                
            }
            // If new home is in a region different from the current home region...
            if (h.region != homeRegion) {
                // ...then the household must first move to the new region (note that for death probability purposes,
                // this change will only take effect in the next time step)...
                homeRegion.households.remove(this);
                homeRegion = h.region;
                homeRegion.households.add(this);
            }
            // ...and then move in to the house
            home = h;
            h.resident = this;
        // If owning a home and having the BTL gene...
        } else if (behaviour.isPropertyInvestor()) {
            // ...decide whether to sell the inherited house
            if (decideToSellHouse(h)) {
                putHouseForSale(h);
            }
            // ...and put it to rent (temporarily, if trying to sell it, or permanently, if not trying to sell it)
            h.region.houseRentalMarket.offer(h, behaviour.buyToLetRent(h.getQuality(), h.region), false);
        // If being an owner-occupier, put inherited house for sale and also for rent temporarily
        } else {
            putHouseForSale(h);
            h.region.houseRentalMarket.offer(h, behaviour.buyToLetRent(h.getQuality(), h.region), false);
        }
    }

    //----- Helpers -----//

    public double getAge() { return age; }

    void ageOneMonth() { age += 1.0/config.constants.MONTHS_IN_YEAR; }

    public boolean isHomeowner() {
        if(home == null) return(false);
        return(home.owner == this);
    }

    public boolean isRenting() {
        if(home == null) return(false);
        return(home.owner != this);
    }

    public boolean isInSocialHousing() { return home == null; }

    boolean isFirstTimeBuyer() { return isFirstTimeBuyer; }

    public boolean isBankrupt() { return isBankrupt; }

    public double getBankBalance() { return bankBalance; }

    public House getHome() { return home; }

    public Map<House, PaymentAgreement> getHousePayments() { return housePayments; }

    public double getAnnualGrossEmploymentIncome() { return annualGrossEmploymentIncome; }

    public double getMonthlyGrossEmploymentIncome() { return monthlyGrossEmploymentIncome; }

    /***
     * @return Number of properties this household currently has on the sale market
     */
    public int nPropertiesForSale() {
        int n=0;
        for(House h : housePayments.keySet()) {
            if(h.isOnMarket()) ++n;
        }
        return(n);
    }

    /**
     * Counts the number of houses owned by this household. By definition of the model, all households owning property
     * do also own a home, i.e ., they live in one of their owned properties and thus only non-homeless households can
     * own any property. As a consequence, the number of houses owned by renters and households in social housing is 0
     *
     * @return Number of houses owned by this household
     */
    public int getNProperties() {
        if (isHomeowner()) {
            return housePayments.size();
        } else {
            return 0;
        }
    }

    /***
     * @return Current mark-to-market (with exponentially averaged prices per quality) equity in this household's home.
     */
    double getHomeEquity() {
        if(!isHomeowner()) return(0.0);
        return home.region.regionalHousingMarketStats.getExpAvSalePriceForQuality(home.getQuality())
                - mortgageFor(home).principal;
    }
    
    public MortgageAgreement mortgageFor(House h) {
        PaymentAgreement payment = housePayments.get(h);
        if(payment instanceof MortgageAgreement) {
            return((MortgageAgreement)payment);
        }
        return(null);
    }

    public double monthlyPaymentOn(House h) {
        PaymentAgreement payment = housePayments.get(h);
        if(payment != null) {
            return(payment.monthlyPayment);
        }
        return(0.0);        
    }

    public Region getJobRegion() { return jobRegion; }

}
