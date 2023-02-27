package com.example.superTrendDemo;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;

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

	public static void main(String[] args) {

		// Change Daily by hitting
		// https://kite.trade/connect/login?api_key=n2tkas121jwtsnce
		String request_token = "58c4tg8UGjMWs2TEQmdyiaTpFLS8VXIi";

		// Configurable Values
		String apiKey = "n2tkas121jwtsnce";
		String apiSecretKey = "pmocmymbss3odcwxqwvtohosgwinut6v";
		String appUserid = "LUY532";
		String bankNiftyToken = "64087559";
		int startHrs = 17;
		int startMins = 6;
		int startSecs = 00;
		int interval = 1;
		double multiplier = 2;
		int lookbackPeriod = 7;

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
			placeOrder(kiteConnect);

			System.exit(0);

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
							System.out.println("Last candle data updated into Bar series at : " + new java.util.Date()
									+ " as open = " + lastCandleData.open + " high = " + lastCandleData.high + " low = "
									+ +lastCandleData.low + "close" + +lastCandleData.close + " time is "
									+ lastCandleData.timeStamp);
						}

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
		HistoricalData historicalData = kiteConnect.getHistoricalData(
				Date.from(LocalDateTime.now().minusDays(2).atZone(ZoneId.systemDefault()).toInstant()),
				Date.from(LocalDateTime.now().atZone(ZoneId.systemDefault()).toInstant()), bankNiftyToken, "minute",
				false);
		BarSeries series = new BaseBarSeries("banknifty");

		for (HistoricalData histData : historicalData.dataArrayList) {
			series.addBar(getFormattedTime(histData.timeStamp), (Number) histData.open, (Number) histData.high,
					(Number) histData.low, (Number) histData.close);
			SuperTrend st = new SuperTrend(series, multiplier, lookbackPeriod);
			// System.out.println(st.toString());
			if (series.getBarCount() > 7) {
				System.out.println("Candle time is = " + histData.timeStamp);
				System.out.println("superTrend value = " + st.getValue(series.getBarCount() - 1));
				System.out.println("isGreen = " + st.getIsGreen(series.getBarCount() - 1));
				System.out.println("superTrend direction " + st.getSignal(series.getBarCount() - 1));
				if (st.getSignal(series.getBarCount() - 2) != null && st.getSignal(series.getBarCount() - 2) != "") {
					Bar lastCandle = series.getBar(series.getBarCount() - 1);
					Bar prevCandle = series.getBar(series.getBarCount() - 2);
					System.out.println("lastCandle = " + lastCandle);
					System.out.println("prevCandle = " + prevCandle);
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
	/*
	 * public static void placeOrder(KiteConnect kiteConnect, int quantity, String
	 * orderType, String tradingSymbol, String product, String exchange, String
	 * transactionType, String validity, Double price, Double triggeredPrice) throws
	 * IOException, KiteException {
	 * 
	 * OrderParams orderParams = new OrderParams(); orderParams.quantity = quantity;
	 * orderParams.orderType = orderType; orderParams.tradingsymbol = tradingSymbol;
	 * orderParams.product = product; orderParams.exchange = exchange;
	 * orderParams.transactionType = transactionType; orderParams.validity =
	 * validity; orderParams.price = price; orderParams.triggerPrice =
	 * triggeredPrice; orderParams.tag = "myTag"; orderParams.disclosedQuantity = 0;
	 * Order order = kiteConnect.placeOrder(orderParams, Constants.VARIETY_REGULAR);
	 * System.out.println(order.orderId); alertCandleData.clear(); }
	 */

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
