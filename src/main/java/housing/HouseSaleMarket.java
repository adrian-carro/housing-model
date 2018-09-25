package housing;

import java.util.Iterator;

import org.apache.commons.math3.random.MersenneTwister;
import utilities.PriorityQueue2D;

/*******************************************************
 * Class that represents market for houses for-sale.
 * 
 * @author daniel, Adrian Carro
 *
 *****************************************************/
public class HouseSaleMarket extends HousingMarket {

	private Config	                    			config; // Private field to receive the Model's configuration parameters object
	private Region                                  region;
    private PriorityQueue2D<HousingMarketRecord>    offersPY;

	HouseSaleMarket(Config config, MersenneTwister rand, Region region) {
	    super(config, rand, region);
	    this.config = config;
	    this.region = region;
		offersPY = new PriorityQueue2D<>(new HousingMarketRecord.PYComparator());
	}
	
	@Override
	public void init() {
		super.init();
		offersPY.clear();
	}
		
	/**
	 * This method deals with doing all the stuff necessary whenever a house gets sold.
	 */
	public void completeTransaction(HouseBidderRecord purchase, HouseOfferRecord sale) {
        // TODO: Revise if it makes sense to have recordTransaction as a separate method from recordSale
		region.regionalHousingMarketStats.recordTransaction(sale);
		sale.getHouse().saleRecord = null;
		Household buyer = purchase.getBidder();
		if(buyer == sale.getHouse().owner) return; // TODO: Shouldn't this if be the first line in this method?
		sale.getHouse().owner.completeHouseSale(sale);
		buyer.completeHousePurchase(sale);
        region.regionalHousingMarketStats.recordSale(purchase, sale);
		sale.getHouse().owner = buyer;
	}

	@Override
	public HouseOfferRecord offer(House house, double price, boolean BTLOffer) {
		HouseOfferRecord hsr = super.offer(house, price, BTLOffer);
		offersPY.add(hsr);
		house.putForSale(hsr);
		return(hsr);
	}
	
	@Override
	public void removeOffer(HouseOfferRecord hsr) {
		super.removeOffer(hsr);
		offersPY.remove(hsr);
		hsr.getHouse().resetSaleRecord();
	}
	
	@Override
	public void updateOffer(HouseOfferRecord hsr, double newPrice) {
		offersPY.remove(hsr);
		super.updateOffer(hsr, newPrice);
		offersPY.add(hsr);
	}

    /**
     * This method overrides the main simulation step in order to sort the price-yield priorities.
     */
    @Override
    void clearMarket() {
        // Before any use, priorities must be sorted by filling in the uncoveredElements TreeSet at the corresponding
        // PriorityQueue2D. In particular, we sort here the price-yield priorities
        offersPY.sortPriorities();
        // Then continue with the normal HousingMarket clearMarket mechanism
        super.clearMarket();
    }

	@Override
	protected HouseOfferRecord getBestOffer(HouseBidderRecord bid) {
        if (bid.isBTLBid()) { // BTL bidder (yield driven)
			HouseOfferRecord bestOffer = (HouseOfferRecord)offersPY.peek(bid);
			if (bestOffer != null) {
					double minDownpayment = bestOffer.getPrice()*(1.0
                            - region.regionalRentalMarketStats.getExpAvFlowYield()
                            /(Model.centralBank.getInterestCoverRatioLimit(false)
                                    *config.CENTRAL_BANK_BTL_STRESSED_INTEREST));
					if (bid.getBidder().getBankBalance() >= minDownpayment) {
						return bestOffer;
					}
			}
			return null;
		} else { // must be OO buyer (quality driven)
			return super.getBestOffer(bid);
		}
	}

    /**
     * Overrides corresponding method at HousingMarket in order to remove successfully matched and cleared offers from
     * the offersPY queue
     *
     * @param record Iterator over the HousingMarketRecord objects contained in offersPQ
     * @param offer Offer to remove from queues
     */
	@Override
    void removeOfferFromQueues(Iterator<HousingMarketRecord> record, HouseOfferRecord offer) {
        record.remove();
        offersPY.remove(offer);
    }

	/*******************************************
	 * Make a bid on the market as a Buy-to-let investor
	 *  (i.e. make an offer on a (yet to be decided) house).
	 * 
	 * @param buyer The household that is making the bid.
	 * @param maxPrice The maximum price that the household is willing to pay.
	 ******************************************/
	void BTLbid(Household buyer, double maxPrice) { bids.add(new HouseBidderRecord(buyer, maxPrice, true)); }
}
