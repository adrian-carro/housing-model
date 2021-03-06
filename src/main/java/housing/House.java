package housing;

/**************************************************************************************************
 * Class to represent a house with all its intrinsic characteristics.
 *
 * @author daniel, Adrian Carro
 *
 *************************************************************************************************/
public class House implements Comparable<House> {

    //------------------//
    //----- Fields -----//
    //------------------//

    private static int 	id_pool = 0;

    public IHouseOwner  owner;
    public Household    resident;
    public Region       region;
    public int          id;

    HouseOfferRecord    saleRecord;
    HouseOfferRecord    rentalRecord;

    private int         quality;

    //------------------------//
    //----- Constructors -----//
    //------------------------//

    /**
     * Creates a house of quality quality in region region
     *
     * @param region Reference to the region where the house sits
     * @param quality Quality band characterizing the house
     */
	public House(Region region, int quality) {
		this.id = ++id_pool;
        this.owner = null;
        this.resident = null;
        this.region = region;
		this.quality = quality;
	}

    //-------------------//
    //----- Methods -----//
    //-------------------//

	boolean isOnMarket() { return saleRecord != null; }

	HouseOfferRecord getSaleRecord() { return saleRecord; }

	HouseOfferRecord getRentalRecord() { return rentalRecord; }

	boolean isOnRentalMarket() { return rentalRecord != null; }
    void putForSale(HouseOfferRecord saleRecord) { this.saleRecord = saleRecord; }

	void resetSaleRecord() { saleRecord = null; }
    void putForRent(HouseOfferRecord rentalRecord) { this.rentalRecord = rentalRecord; }

	void resetRentalRecord() { rentalRecord = null; }

	public int getQuality() { return quality; }

    public Region getRegion() { return region; }

	@Override
	public int compareTo(House o) { return((int)Math.signum(id-o.id)); }
	
}
