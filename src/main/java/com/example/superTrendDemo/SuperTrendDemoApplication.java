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
public class SuperTrendDemoApplication {

	private static Map<String, String> alertCandleData = new ConcurrentHashMap<String, String>();
	public static final int PERIOD_DAYS = 60;

	public static void main(String[] args) {

		XSSFWorkbook workbook;
		XSSFSheet sheet;

		// https://kite.trade/connect/login?api_key=n2tkas121jwtsnce
		String request_token = "WbV7DtMKowr37VHL4SkoSGR7sMn0e5yJ";

		// Configurable Values
		String apiKey = "n2tkas121jwtsnce";
		String apiSecretKey = "pmocmymbss3odcwxqwvtohosgwinut6v";
		String appUserid = "LUY532";
		String bankNiftyToken = "260105";
		int startHrs = 15;
		int startMins = 21;
		int startSecs = 00;
		int interval = 2;
		double multiplier = 3;
		int lookbackPeriod = 7;

		int profitCount = 0;
		int lossCount = 0;

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
			timer.schedule(new TimerTask() {
				@Override
				public void run() {
					System.out.println("Running task based on interval at : " + new java.util.Date());
					try {
						final HistoricalData currentDayData = kiteConnect.getHistoricalData(
								Date.from(LocalDateTime.now().minusMinutes(interval).atZone(ZoneId.systemDefault())
										.toInstant()),
								Date.from(LocalDateTime.now().atZone(ZoneId.systemDefault()).toInstant()),
								bankNiftyToken, "minute", false);
						System.out.println("Got historical data for last 4 minutes at : " + new java.util.Date());
						HistoricalData lastCandleData = currentDayData.dataArrayList
								.get(currentDayData.dataArrayList.size() - 1);

						if (series.getLastBar().getEndTime().isBefore(ZonedDateTime.parse((lastCandleData.timeStamp),
								DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssZ")))) {
							series.addBar(getFormattedTime(lastCandleData.timeStamp), (Number) lastCandleData.open,
									(Number) lastCandleData.high, (Number) lastCandleData.low,
									(Number) lastCandleData.close);
						}

						System.out.println("Last candle data updated into Bar series at : " + new java.util.Date()
								+ " as open = " + lastCandleData.open + " high = " + lastCandleData.high + " low = "
								+ +lastCandleData.low + "close" + +lastCandleData.close + " time is "
								+ lastCandleData.timeStamp);

						// Calculating superTrend value and find the signal for direction
						SuperTrend st = new SuperTrend(series, multiplier, lookbackPeriod);
						boolean currentCandleIsGreen = st.getIsGreen(series.getBarCount() - 2);
						boolean prevCandleIsGreen = st.getIsGreen(series.getBarCount() - 3);

						if (currentCandleIsGreen != prevCandleIsGreen) {
							System.out.println("There is change in trend 2 candles before this time : "
									+ new java.util.Date() + " and the new direction is " + currentCandleIsGreen);

							double firstHigh = series.getBar(series.getBarCount() - 1).getHighPrice().doubleValue();
							double secondHigh = series.getBar(series.getBarCount() - 2).getHighPrice().doubleValue();
							double firstLow = series.getBar(series.getBarCount() - 1).getLowPrice().doubleValue();
							double secondLow = series.getBar(series.getBarCount() - 2).getLowPrice().doubleValue();
							alertCandleData.put("finalHigh",
									String.valueOf((firstHigh > secondHigh) ? firstHigh : secondHigh));
							alertCandleData.put("finalLow",
									String.valueOf((firstLow < secondLow) ? firstLow : secondLow));
							alertCandleData.put("direction", String.valueOf(currentCandleIsGreen));
							System.out.println(" FH=" + firstHigh + " SH = " + secondHigh + " FL = " + firstLow
									+ " SL = " + secondLow);
							initiateTickerData(kiteConnect, tokens);
						}
					} catch (JSONException | IOException | KiteException e) {
						System.out.println("Algo faced an error: " + e.getMessage());
						e.printStackTrace();
					}
				}
			}, Date.from(today.withHour(startHrs).withMinute(startMins).withSecond(startSecs)
					.atZone(ZoneId.systemDefault()).toInstant()), interval * 60 * 1000);

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
						&& series.getBarCount() - 1 > 7
						&& series.getBar(i).getHighPrice().doubleValue() > supertrend.finalLowerBand(i)
						&& series.getBar(i).getLowPrice().doubleValue() < supertrend.finalLowerBand(i)
						&& dayTrades < 1) {
					row.createCell(7).setCellValue("Enter Buy Trade");
					inTrade = true;
					isItBuy = true;
					// eligible = false;
					drawdownValues.clear();
					runupValues.clear();
					dayTrades = dayTrades + 1;
					entryPrice = series.getBar(i).getClosePrice().doubleValue();
					row.createCell(8).setCellValue(supertrend.finalLowerBand(i));

				} else if (validMarketHour && !inTrade && eligible && supertrend.getIsGreen(i) == false
						&& series.getBarCount() - 1 > 7
						&& series.getBar(i).getHighPrice().doubleValue() > supertrend.finalUpperBand(i)
						&& series.getBar(i).getLowPrice().doubleValue() < supertrend.finalUpperBand(i)
						&& dayTrades < 1) {
					row.createCell(7).setCellValue("Enter Sell Trade");
					inTrade = true;
					isItBuy = false;

					// eligible = false;
					drawdownValues.clear();
					runupValues.clear();
					dayTrades = dayTrades + 1;
					entryPrice = series.getBar(i).getClosePrice().doubleValue();
					row.createCell(8).setCellValue(supertrend.finalUpperBand(i));
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

	private static void initiateTickerData(KiteConnect kiteConnect, ArrayList<Long> tokens) {
		KiteTicker tickerProvider = new KiteTicker(kiteConnect.getAccessToken(), kiteConnect.getApiKey());
		tickerProvider.setOnConnectedListener((OnConnect) new OnConnect() {
			@Override
			public void onConnected() {
				tickerProvider.subscribe(tokens);
				tickerProvider.setMode(tokens, KiteTicker.modeFull);
			}
		});
		tickerProvider.setOnDisconnectedListener((OnDisconnect) new OnDisconnect() {
			@Override
			public void onDisconnected() {
				System.out.println("inside on connected");
			}
		});
		tickerProvider.setOnOrderUpdateListener((OnOrderUpdate) new OnOrderUpdate() {
			@Override
			public void onOrderUpdate(Order order) {
				System.out.println("order update " + order.orderId);
			}
		});

		// reading the live stream data
		tickerProvider.setOnTickerArrivalListener((OnTicks) new OnTicks() {
			@Override
			public void onTicks(ArrayList<Tick> ticks) {
				try {

					System.out.println(alertCandleData.get("finalHigh"));
					System.out.println(alertCandleData.get("finalLow"));

					if (ticks.size() > 0 && alertCandleData.size() == 3) {

						if (alertCandleData.get("direction").equalsIgnoreCase("true") && ticks.get(0)
								.getLastTradedPrice() > Double.parseDouble(alertCandleData.get("finalHigh"))) {
							// placeOrder("buy", ticks.get(0).getLastTradedPrice(), kiteConnect);
							System.out.println("Place CE order at " + new java.util.Date());
							alertCandleData.clear();
						} else if (alertCandleData.get("direction").equalsIgnoreCase("false") && ticks.get(0)
								.getLastTradedPrice() < Double.parseDouble(alertCandleData.get("finalLow"))) {
							// placeOrder("sell", ticks.get(0).getLastTradedPrice(), kiteConnect);
							System.out.println("Place PE order at " + new java.util.Date());
							alertCandleData.clear();
						}
					}
				} catch (JSONException e) {
					e.printStackTrace();
				}
			}
		});

		tickerProvider.setTryReconnection(true);
		try {
			tickerProvider.setMaximumRetries(10);
			tickerProvider.setMaximumRetryInterval(30);
		} catch (KiteException e) {
			e.printStackTrace();
		}

		tickerProvider.connect();
		boolean isConnected = tickerProvider.isConnectionOpen();
		System.out.println(isConnected);
		tickerProvider.setMode(tokens, KiteTicker.modeLTP);
	}

	private static BarSeries getHistoricalData(String bankNiftyToken, int interval, double multiplier,
			int lookbackPeriod, KiteConnect kiteConnect) throws KiteException, IOException {
		// Get historical data since yesterday
		BarSeries series = new BaseBarSeries("banknifty");

		// Get historical data since yesterday
		for (int i = 1; i > 0; i--) {
			HistoricalData historicalData = kiteConnect.getHistoricalData(
					Date.from(
							LocalDateTime.now().minusDays(i * PERIOD_DAYS).atZone(ZoneId.systemDefault()).toInstant()),
					Date.from(LocalDateTime.now().minusDays((i - 1) * PERIOD_DAYS).atZone(ZoneId.systemDefault())
							.toInstant()),
					bankNiftyToken, "minute", false);
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

	// API for placing the order
	public static void placeOrder(KiteConnect kiteConnect) throws JSONException, IOException, KiteException {
		OrderParams orderParams = new OrderParams();
		orderParams.quantity = Constants.QUANTITY;
		orderParams.orderType = Constants.ORDER_TYPE_LIMIT;
		orderParams.tradingsymbol = Constants.TRADINGSYMBOL;
		orderParams.product = Constants.PRODUCT_MIS;
		orderParams.exchange = Constants.EXCHANGE_NSE;
		orderParams.transactionType = Constants.TRANSACTION_TYPE_SELL;
		orderParams.validity = Constants.VALIDITY_DAY;
		orderParams.price = Constants.PRICE;
		orderParams.triggerPrice = Constants.TRIGGEREDPRICE;
		orderParams.tag = "myTag";
		orderParams.disclosedQuantity = 0;
		Order order = kiteConnect.placeOrder(orderParams, Constants.VARIETY_REGULAR);
		System.out.println(order.orderId);
		System.out.println(order.status);

		alertCandleData.clear();
	}

}