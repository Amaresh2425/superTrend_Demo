package com.example.superTrendDemo;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.json.JSONException;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeries;

import com.zerodhatech.kiteconnect.KiteConnect;
import com.zerodhatech.kiteconnect.kitehttp.exceptions.KiteException;
import com.zerodhatech.models.HistoricalData;
import com.zerodhatech.models.Order;
import com.zerodhatech.models.OrderParams;
import com.zerodhatech.models.Tick;
import com.zerodhatech.models.User;
import com.zerodhatech.ticker.KiteTicker;
import com.zerodhatech.ticker.OnConnect;
import com.zerodhatech.ticker.OnDisconnect;
import com.zerodhatech.ticker.OnOrderUpdate;
import com.zerodhatech.ticker.OnTicks;

@SpringBootApplication
public class SuperTrendDemoCandelApplication {

	private static Map<String, String> alertCandleData = new ConcurrentHashMap<String, String>();
	public static final int PERIOD_DAYS = 60;

	public static void main(String[] args) {

		XSSFWorkbook workbook;
		XSSFSheet sheet;

		// https://kite.trade/connect/login?api_key=n2tkas121jwtsnce
		String request_token = "R2mBGb4cAlL3tOaNY1dXOxs7kATR3xkB";

		// Configurable Values
		String apiKey = "n2tkas121jwtsnce";
		String apiSecretKey = "pmocmymbss3odcwxqwvtohosgwinut6v";
		String appUserid = "LUY532";
		String bankNiftyToken = "260105";
		int interval = 2;
		double multiplier = 2;
		int lookbackPeriod = 7;

		int profitCount = 0;
		int lossCount = 0;

		boolean currentCandleIsGreen = false;
		boolean prevCandleIsGreen = false;

		try {
			// Create kite connect object by creating session.
			KiteConnect kiteConnect = new KiteConnect(apiKey);
			kiteConnect.setUserId(appUserid);
			User user = kiteConnect.generateSession(request_token, apiSecretKey);
			kiteConnect.setAccessToken(user.accessToken);
			kiteConnect.setPublicToken(user.publicToken);
			System.out.println("User session created and algo started at : " + new java.util.Date());

			/*
			 * placeOrder(kiteConnect, Constants.QUANTITY, Constants.ORDER_TYPE_LIMIT,
			 * Constants.TRADINGSYMBOL, Constants.PRODUCT_NRML, Constants.EXCHANGE_NSE,
			 * Constants.TRANSACTION_TYPE_BUY, Constants.VALIDITY_DAY, Constants.PRICE,
			 * Constants.TRIGGEREDPRICE);
			 */
			// placeOrder(kiteConnect);

			// System.exit(0);

			// Get all tokens that need to be subscribed to.
			ArrayList<Long> tokens = new ArrayList<Long>();
			tokens.add(Long.parseLong(bankNiftyToken));

			BarSeries series = getHistoricalData(bankNiftyToken, interval, multiplier, lookbackPeriod, kiteConnect);
			System.out.println("Got Historical Data and updated at : " + new java.util.Date());

			// Get time and schedule task to run periodically.
			LocalDateTime today = LocalDateTime.now();
			Timer timer = new Timer();

			SuperTrend st = new SuperTrend(series, multiplier, lookbackPeriod);
			for (int i = 1; i < series.getBarCount(); i++) {
				currentCandleIsGreen = st.getIsGreen(i);
				prevCandleIsGreen = st.getIsGreen(i - 1);

				if (currentCandleIsGreen != prevCandleIsGreen) {
					System.out.println("There is change in trend 2 candles before this time : " + new java.util.Date()
							+ " and the new direction is " + currentCandleIsGreen);

					double firstHigh = series.getBar(i).getHighPrice().doubleValue();
					double secondHigh = series.getBar(i + 1).getHighPrice().doubleValue();
					double firstLow = series.getBar(i).getLowPrice().doubleValue();
					double secondLow = series.getBar(i + 1).getLowPrice().doubleValue();
					alertCandleData.put("finalHigh", String.valueOf((firstHigh > secondHigh) ? firstHigh : secondHigh));
					alertCandleData.put("finalLow", String.valueOf((firstLow < secondLow) ? firstLow : secondLow));
					alertCandleData.put("direction", String.valueOf(currentCandleIsGreen));
					System.out.println(
							" FH=" + firstHigh + " SH = " + secondHigh + " FL = " + firstLow + " SL = " + secondLow);
				}
			}

			// exporting data to the xsl sheet
			workbook = new XSSFWorkbook();
			sheet = workbook.createSheet("superTrendData");
			XSSFRow row;
			Boolean inTrade = false;
			Boolean isItBuy = false;
			Double entryPrice = 0.0;
			Boolean eligible = false;
			Boolean validMarketHour = false;
			Boolean prevTradeLoss = false;
			int dayTrades = 0;
			SuperTrend supertrend = new SuperTrend(series);

			ArrayList<Double> runupValues = new ArrayList<Double>();
			ArrayList<Double> drawdownValues = new ArrayList<Double>();
			row = sheet.createRow(0);
			row.createCell(0).setCellValue("Time");
			row.createCell(1).setCellValue("High");
			row.createCell(2).setCellValue("Low");
			row.createCell(3).setCellValue("Open");
			row.createCell(4).setCellValue("Close");
			row.createCell(5).setCellValue("Green?");
			row.createCell(6).setCellValue("Changed to?");
			row.createCell(7).setCellValue("Entry");
			row.createCell(8).setCellValue("Supertrend Value");
			row.createCell(9).setCellValue("Run Up");
			row.createCell(10).setCellValue("Draw down");
			row.createCell(11).setCellValue("Max Run Up");
			row.createCell(12).setCellValue("Max Draw down");
			row.createCell(13).setCellValue("Result");
			row.createCell(14).setCellValue("Total Profit Trades");
			row.createCell(15).setCellValue("Total Loss Trades");
			row.createCell(16).setCellValue("Net Profit Trades");

			for (int i = 0; i < series.getBarCount() - 1; i++) {
				row = sheet.createRow(i + 1);
				row.createCell(0).setCellValue(series.getBar(i).getDateName().toString());
				row.createCell(1).setCellValue(series.getBar(i).getHighPrice().toString());
				row.createCell(2).setCellValue(series.getBar(i).getLowPrice().toString());
				row.createCell(3).setCellValue(series.getBar(i).getOpenPrice().toString());
				row.createCell(4).setCellValue(series.getBar(i).getClosePrice().toString());
				row.createCell(5).setCellValue(supertrend.getIsGreen(i));
				if (supertrend.getIsGreen(i) == true && supertrend.getIsGreen(i + 1) == false) {
					row.createCell(6).setCellValue("Changed to Red");
					eligible = true;
				} else if (supertrend.getIsGreen(i) == false && supertrend.getIsGreen(i + 1) == true) {
					row.createCell(6).setCellValue("Changed to Green");
					eligible = true;
				}

				if (series.getBar(i).getBeginTime().getHour() >= 9 && series.getBar(i).getBeginTime().getHour() < 15) {
					validMarketHour = true;
					if (series.getBar(i).getBeginTime().getHour() == 9
							&& series.getBar(i).getBeginTime().getMinute() < 30) {
						validMarketHour = false;
					}

				} else {
					validMarketHour = false;

					dayTrades = 0;
				}

				if (validMarketHour && !inTrade && eligible && supertrend.getIsGreen(i) == true
						&& series.getBarCount() - 1 > 7 && series.getBar(i).getHighPrice().doubleValue() > Double
								.parseDouble(alertCandleData.get("finalHigh"))
						&& dayTrades < 3) {
					row.createCell(7).setCellValue("Enter Buy Trade");
					inTrade = true;
					isItBuy = true;
					// eligible = false;
					drawdownValues.clear();
					runupValues.clear();
					dayTrades = dayTrades + 1;
					entryPrice = series.getBar(i).getHighPrice().doubleValue();
					row.createCell(8).setCellValue(series.getBar(i).getHighPrice().doubleValue());

				} else if (validMarketHour && !inTrade && eligible && supertrend.getIsGreen(i) == false

						&& series.getBarCount() - 1 > 7 && series.getBar(i).getLowPrice().doubleValue() < Double
								.parseDouble(alertCandleData.get("finalLow"))
						&& dayTrades < 3) {
					row.createCell(7).setCellValue("Enter Sell Trade");
					inTrade = true;
					isItBuy = false;

					// eligible = false;
					drawdownValues.clear();
					runupValues.clear();
					dayTrades = dayTrades + 1;
					entryPrice = series.getBar(i).getLowPrice().doubleValue();
					row.createCell(8).setCellValue(series.getBar(i).getLowPrice().doubleValue());
				}
				// profit
				if (inTrade && isItBuy) {
					runupValues.add(series.getBar(i).getHighPrice().doubleValue() - entryPrice);
					row.createCell(9).setCellValue(series.getBar(i).getHighPrice().doubleValue() - entryPrice);

					// draw down
					drawdownValues.add(entryPrice - series.getBar(i).getLowPrice().doubleValue());
					row.createCell(10).setCellValue(entryPrice - series.getBar(i).getLowPrice().doubleValue());
				}
				if (inTrade && !isItBuy) {
					runupValues.add(entryPrice - series.getBar(i).getLowPrice().doubleValue());

					row.createCell(9).setCellValue(entryPrice - series.getBar(i).getLowPrice().doubleValue());
					// draw down
					drawdownValues.add(series.getBar(i).getHighPrice().doubleValue() - entryPrice);
					row.createCell(10).setCellValue(series.getBar(i).getHighPrice().doubleValue() - entryPrice);
				}
				if (inTrade) {
					row.createCell(11).setCellValue(Collections.max(runupValues));
					row.createCell(12).setCellValue(Collections.max(drawdownValues));
				}

				if (inTrade && (Collections.max(runupValues) > 80)) {
					inTrade = false;
					row.createCell(13).setCellValue("profit");
					profitCount++;
					if (prevTradeLoss) {
						// profitCount++;
					}
					prevTradeLoss = false;
				}

				if (inTrade && (Collections.max(drawdownValues) > 60)) {
					inTrade = false;
					row.createCell(13).setCellValue("loss");
					lossCount++;
					if (prevTradeLoss) {
						// lossCount++;
					}
					prevTradeLoss = true;
				}

				row.createCell(14).setCellValue(profitCount);
				row.createCell(15).setCellValue(lossCount);
				row.createCell(16).setCellValue(profitCount - lossCount);

			}
			System.out.println("total number of profit trades = " + profitCount);
			System.out.println("total number of loss trades = " + lossCount);
			System.out.println("net profit trades = " + (profitCount - lossCount));

			FileOutputStream out = new FileOutputStream(
					new File("C:/Users/risha/Desktop/SUPERTREND_STRATEGY/supertrend.xlsx"));

			workbook.write(out);
			out.close();

		} catch (JSONException | IOException |

				KiteException e) {
			System.out.println("Algo faced an error: " + e.getMessage());
			e.printStackTrace();
		}
	}

	private static BarSeries getHistoricalData(String bankNiftyToken, int interval, double multiplier,
			int lookbackPeriod, KiteConnect kiteConnect) throws KiteException, IOException {
		// Get historical data since yesterday
		BarSeries series = new BaseBarSeries("banknifty");

		// Get historical data since yesterday
		for (int i = 24; i > 0; i--) {
			HistoricalData historicalData = kiteConnect.getHistoricalData(
					Date.from(
							LocalDateTime.now().minusDays(i * PERIOD_DAYS).atZone(ZoneId.systemDefault()).toInstant()),
					Date.from(LocalDateTime.now().minusDays((i - 1) * PERIOD_DAYS).atZone(ZoneId.systemDefault())
							.toInstant()),
					bankNiftyToken, interval + "minute", false);
			if (null != historicalData && historicalData.dataArrayList.size() > 0) {
				for (HistoricalData histData : historicalData.dataArrayList) {

					series.addBar(getFormattedTime(histData.timeStamp), (Number) histData.open, (Number) histData.high,
							(Number) histData.low, (Number) histData.close);

				}
			}

		}
		return series;
	}

	// data formatter
	private static ZonedDateTime getFormattedTime(String dateStr) {
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssZ");
		ZonedDateTime zonedDateTime = ZonedDateTime.parse(dateStr, formatter);
		return zonedDateTime;
	}
}
