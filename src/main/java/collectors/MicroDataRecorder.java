package collectors;

import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;

public class MicroDataRecorder {

    //------------------//
    //----- Fields -----//
    //------------------//

    private String outputFolder;

    private PrintWriter outfileEmploymentIncome;
    private PrintWriter outfileRentalIncome;
    private PrintWriter outfileBankBalance;
    private PrintWriter outfileHousingWealth;
    private PrintWriter outfileNHousesOwned;
    private PrintWriter outfileAge;
    private PrintWriter outfileSavingRate;

    //------------------------//
    //----- Constructors -----//
    //------------------------//

    public MicroDataRecorder(String outputFolder) { this.outputFolder = outputFolder; }

    //-------------------//
    //----- Methods -----//
    //-------------------//

    public void openSingleRunSingleVariableFiles(int nRun, boolean recordEmploymentIncome, boolean recordRentalIncome,
                                                 boolean recordBankBalance, boolean recordHousingWealth,
                                                 boolean recordNHousesOwned, boolean recordAge,
                                                 boolean recordSavingRate) {
        if (recordEmploymentIncome) {
            try {
                outfileEmploymentIncome = new PrintWriter(outputFolder + "MonthlyGrossEmploymentIncome-run" + nRun
                        + ".csv", "UTF-8");
            } catch (FileNotFoundException | UnsupportedEncodingException e) {
                e.printStackTrace();
            }
        }
        if (recordRentalIncome) {
            try {
                outfileRentalIncome = new PrintWriter(outputFolder + "MonthlyGrossRentalIncome-run" + nRun
                        + ".csv", "UTF-8");
            } catch (FileNotFoundException | UnsupportedEncodingException e) {
                e.printStackTrace();
            }
        }
        if (recordBankBalance) {
            try {
                outfileBankBalance = new PrintWriter(outputFolder + "BankBalance-run" + nRun
                        + ".csv", "UTF-8");
            } catch (FileNotFoundException | UnsupportedEncodingException e) {
                e.printStackTrace();
            }
        }
        if (recordHousingWealth) {
            try {
                outfileHousingWealth = new PrintWriter(outputFolder + "HousingWealth-run" + nRun
                        + ".csv", "UTF-8");
            } catch (FileNotFoundException | UnsupportedEncodingException e) {
                e.printStackTrace();
            }
        }
        if (recordNHousesOwned) {
            try {
                outfileNHousesOwned = new PrintWriter(outputFolder + "NHousesOwned-run" + nRun
                        + ".csv", "UTF-8");
            } catch (FileNotFoundException | UnsupportedEncodingException e) {
                e.printStackTrace();
            }
        }
        if (recordAge) {
            try {
                outfileAge = new PrintWriter(outputFolder + "Age-run" + nRun
                        + ".csv", "UTF-8");
            } catch (FileNotFoundException | UnsupportedEncodingException e) {
                e.printStackTrace();
            }
        }
        if (recordSavingRate) {
            try {
                outfileSavingRate = new PrintWriter(outputFolder + "SavingRate-run" + nRun
                        + ".csv", "UTF-8");
            } catch (FileNotFoundException | UnsupportedEncodingException e) {
                e.printStackTrace();
            }
        }
    }

    void timeStampSingleRunSingleVariableFiles(int time, boolean recordEmploymentIncome, boolean recordRentalIncome,
                                               boolean recordBankBalance, boolean recordHousingWealth,
                                               boolean recordNHousesOwned, boolean recordAge,
                                               boolean recordSavingRate) {
        if (time % 100 == 0) {
            if (recordEmploymentIncome) {
                if (time != 0) {
                    outfileEmploymentIncome.println("");
                }
                outfileEmploymentIncome.print(time);
            }
            if (recordRentalIncome) {
                if (time != 0) {
                    outfileRentalIncome.println("");
                }
                outfileRentalIncome.print(time);
            }
            if (recordBankBalance) {
                if (time != 0) {
                    outfileBankBalance.println("");
                }
                outfileBankBalance.print(time);
            }
            if (recordHousingWealth) {
                if (time != 0) {
                    outfileHousingWealth.println("");
                }
                outfileHousingWealth.print(time);
            }
            if (recordNHousesOwned) {
                if (time != 0) {
                    outfileNHousesOwned.println("");
                }
                outfileNHousesOwned.print(time);
            }
            if (recordAge) {
                if (time != 0) {
                    outfileAge.println("");
                }
                outfileAge.print(time);
            }
            if (recordSavingRate) {
                if (time != 0) {
                    outfileSavingRate.println("");
                }
                outfileSavingRate.print(time);
            }
        }
    }

    void recordEmploymentIncome(int time, double monthlyGrossEmploymentIncome) {
        if (time % 100 == 0) {
            outfileEmploymentIncome.print(", " + monthlyGrossEmploymentIncome);
        }
    }

    void recordRentalIncome(int time, double monthlyGrossRentalIncome) {
        if (time % 100 == 0) {
            outfileRentalIncome.print(", " + monthlyGrossRentalIncome);
        }
    }

	void recordBankBalance(int time, double bankBalance) {
        if (time % 100 == 0) {
            outfileBankBalance.print(", " + bankBalance);
        }
	}

    void recordHousingWealth(int time, double housingWealth) {
        if (time % 100 == 0) {
            outfileHousingWealth.print(", " + housingWealth);
        }
    }

    void recordNHousesOwned(int time, int nHousesOwned) {
        if (time % 100 == 0) {
            outfileNHousesOwned.print(", " + nHousesOwned);
        }
    }

    void recordAge(int time, double age) {
        if (time % 100 == 0) {
            outfileAge.print(", " + age);
        }
    }

    void recordSavingRate(int time, double savingRate) {
        if (time % 100 == 0) {
            outfileSavingRate.print(", " + savingRate);
        }
    }

	public void finishRun(boolean recordEmploymentIncome, boolean recordRentalIncome, boolean recordBankBalance,
                          boolean recordHousingWealth, boolean recordNHousesOwned, boolean recordAge,
                          boolean recordSavingRate) {
        if (recordEmploymentIncome) {
            outfileEmploymentIncome.close();
        }
        if (recordRentalIncome) {
            outfileRentalIncome.close();
        }
        if (recordBankBalance) {
            outfileBankBalance.close();
        }
        if (recordHousingWealth) {
            outfileHousingWealth.close();
        }
        if (recordNHousesOwned) {
            outfileNHousesOwned.close();
        }
        if (recordAge) {
            outfileAge.close();
        }
        if (recordSavingRate) {
            outfileSavingRate.close();
        }
	}
}
