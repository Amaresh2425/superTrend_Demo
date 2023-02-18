package com.example.superTrendDemo;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;

import org.json.JSONException;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeries;
import org.ta4j.core.num.Num;

import com.zerodhatech.kiteconnect.KiteConnect;
import com.zerodhatech.kiteconnect.kitehttp.exceptions.KiteException;
import com.zerodhatech.models.HistoricalData;
import com.zerodhatech.models.Tick;
import com.zerodhatech.models.User;
import com.zerodhatech.ticker.KiteTicker;
import com.zerodhatech.ticker.OnConnect;
import com.zerodhatech.ticker.OnDisconnect;
import com.zerodhatech.ticker.OnOrderUpdate;
import com.zerodhatech.ticker.OnTicks;

@SpringBootApplication
public class SuperTrendDemoApplication {

	public static void main(String[] args) {

		// Change Daily by hitting
		// https://kite.trade/connect/login?api_key=p0zhrqp68wf4qd9j
		String request_token = "uFxrWk7c0irP9O4ZU7Dlw55zyDGE4v6l";

		// Configurable Values
		String apiKey = "p0zhrqp68wf4qd9j";
		String apiSecretKey = "hc13yo68q7bnvivchixnjvud0q5y5fp4";
		String appUserid = "GV1827";
		String bankNiftyToken = "260105";
		int startHrs = 13;
		int startMins = 06;
		int startSecs = 0;
		int interval = 2;
		double multiplier = 3;
		int lookbackPeriod = 7;

		try {

			// Create kite connect object by creating session.
			KiteConnect kiteConnect = new KiteConnect(apiKey);
			kiteConnect.setUserId(appUserid);
			User user = kiteConnect.generateSession(request_token, apiSecretKey);
			kiteConnect.setAccessToken(user.accessToken);
			kiteConnect.setPublicToken(user.publicToken);

			// Get all tokens that need to be subscribed to.
			ArrayList<Long> tokens = new ArrayList<Long>();
			tokens.add(Long.parseLong(bankNiftyToken));

			// Get historical data since yesterday
			HistoricalData historicalData = kiteConnect.getHistoricalData(
					Date.from(LocalDateTime.now().minusDays(1).atZone(ZoneId.systemDefault()).toInstant()),
					Date.from(LocalDateTime.now().atZone(ZoneId.systemDefault()).toInstant()), bankNiftyToken,
					interval + "minute", false);
			BarSeries series = new BaseBarSeries("banknifty");

			for (HistoricalData histData : historicalData.dataArrayList) {
				series.addBar(getFormattedTime(histData.timeStamp), (Number) histData.open, (Number) histData.high,
						(Number) histData.low, (Number) histData.close);
				SuperTrend st = new SuperTrend(series, multiplier, lookbackPeriod);
				// System.out.println(st.toString());
				System.out.println("superTrend value = " + st.getValue(series.getBarCount() - 1));
				if (series.getBarCount() > 7) {
					System.out.println("superTrend direction " + st.getSignal(series.getBarCount() - 1) + " at "
							+ getFormattedTime(histData.timeStamp));
				}

			}

			// Get time and schedule task to run periodically.
			LocalDateTime today = LocalDateTime.now();
			Timer timer = new Timer();

			timer.schedule(new TimerTask() {
				@Override
				public void run() {
					System.out.println("Algo started at: " + new java.util.Date());
					try {
						final HistoricalData currentDayData = kiteConnect.getHistoricalData(
								Date.from(
										LocalDateTime.now().minusMinutes(4).atZone(ZoneId.systemDefault()).toInstant()),
								Date.from(LocalDateTime.now().atZone(ZoneId.systemDefault()).toInstant()),
								bankNiftyToken, interval + "minute", false);

						HistoricalData lastCandleData = currentDayData.dataArrayList
								.get(currentDayData.dataArrayList.size() - 1);

						series.addBar(getFormattedTime(lastCandleData.timeStamp), (Number) lastCandleData.open,
								(Number) lastCandleData.high, (Number) lastCandleData.low,
								(Number) lastCandleData.close);
						System.out.println(" current bar series count value = " + series.getBarCount());

						// calculating the current data superTrend value and find the signal for
						// direction
						SuperTrend st = new SuperTrend(series, multiplier, lookbackPeriod);
						System.out.println("current superTrend value = " + st.getValue(series.getBarCount() - 1));
						System.out.println(
								"on current data superTrend direction " + st.getSignal(series.getBarCount() - 1)
										+ " at " + getFormattedTime(lastCandleData.timeStamp));

						String direction = st.getSignal(series.getBarCount() - 1);
						HistoricalData recentData = lastCandleData;

						if (direction.equalsIgnoreCase("Buy") || direction.equalsIgnoreCase("sell")) {
							Thread.sleep(2000);
							series.addBar(getFormattedTime(recentData.timeStamp), (Number) recentData.open,
									(Number) recentData.high, (Number) recentData.low, (Number) recentData.close);

							KiteTicker tickerProvider = new KiteTicker(kiteConnect.getAccessToken(),
									kiteConnect.getApiKey());
							tickerProvider.setOnConnectedListener(new OnConnect() {
								@Override
								public void onConnected() {
									tickerProvider.subscribe(tokens);
									tickerProvider.setMode(tokens, KiteTicker.modeFull);
								}
							});
							tickerProvider.setOnDisconnectedListener(new OnDisconnect() {
								public void onDisconnected() {
									System.out.println("inside on connected");
								}
							});

							tickerProvider.setOnTickerArrivalListener(new OnTicks() {
								public void onTicks(ArrayList<Tick> ticks) {
									System.out.println("ticks size = " + ticks.size());
									if (ticks.size() > 0) {
										double ticksLow = ticks.get(0).getLowPrice();
										double ticksHigh = ticks.get(0).getHighPrice();
									}

								}
							});
						}
					} catch (JSONException | IOException | KiteException | InterruptedException e) {
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

	// data formatter
	private static ZonedDateTime getFormattedTime(String dateStr) {
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssZ");
		ZonedDateTime zonedDateTime = ZonedDateTime.parse(dateStr, formatter);
		return zonedDateTime;
	}
}
