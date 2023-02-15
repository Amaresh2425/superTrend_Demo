package com.example.superTrendDemo;

import java.io.IOException;
import java.net.http.HttpClient;
import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;

import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.json.JSONException;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import com.zerodhatech.kiteconnect.KiteConnect;
import com.zerodhatech.kiteconnect.kitehttp.exceptions.KiteException;
import com.zerodhatech.models.HistoricalData;
import com.zerodhatech.models.Order;
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
		// read account details

		String api_key = "p0zhrqp68wf4qd9j";

		String api_secret_key = ";hc13yo68q7bnvivchixnjvud0q5y5fp4";

		String request_token = "47ljTUGnkiofnZJDkVgc1NAWEnnPInfN";

		String app_userid = "GV1827";

		Long nifty_token = (long) 260105;

		String date = "2023-02-15 17:20:00";

		KiteConnect kiteConnect = new KiteConnect(api_key);
		kiteConnect.setUserId(app_userid);

		User user;
		try {
			user = kiteConnect.generateSession(request_token, api_secret_key);// generating the session
			kiteConnect.setAccessToken(user.accessToken);
			kiteConnect.setPublicToken(user.publicToken);
			ArrayList<Long> tokens = new ArrayList<>();
			tokens.add(nifty_token);
			DateFormat dateFormatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
			Timer timer = new Timer();

			timer.schedule(new TimerTask() {
				@Override
				public void run() {
					System.out.println("task is running at: " + new java.util.Date());
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
							NumberFormat formatter = new DecimalFormat();
							System.out.println("ticks size = " + ticks.size());
							if (ticks.size() > 0) {
								System.out.println(ticks.get(0).getClosePrice());
								System.out.println(ticks.get(0).getHighPrice());

							}

						}
					});

					tickerProvider.setTryReconnection(true);
					try {
						tickerProvider.setMaximumRetries(10);
						tickerProvider.setMaximumRetryInterval(30);
					} catch (KiteException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}

					tickerProvider.connect();
					boolean isConnected = tickerProvider.isConnectionOpen();
					System.out.println(isConnected);
					tickerProvider.setMode(tokens, KiteTicker.modeLTP);

				}

			}, dateFormatter.parse(date), 180 * 1000); // one

			// reading the historical data
			SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
			Date from = new Date();
			Date to = new Date();
			from = formatter.parse("2023-02-01 09:15:00");
			to = formatter.parse("2023-02-13 15:30:00");
			HistoricalData historicalData = kiteConnect.getHistoricalData(from, to, "nifty_token", "15minute", false);
			System.out.println(historicalData.dataArrayList.size());
			System.out.println(historicalData.dataArrayList.get(0).volume);
			System.out.println(historicalData.dataArrayList.get(historicalData.dataArrayList.size() - 1).volume);
			System.out.println(historicalData.dataArrayList.get(0).close);
			System.out.println(historicalData.dataArrayList.get(0).open);
			System.out.println(historicalData.dataArrayList.get(0).high);
			System.out.println(historicalData.dataArrayList.get(0).low);
			System.out.println(historicalData.dataArrayList.get(0).timeStamp);

		} catch (JSONException | IOException | KiteException | ParseException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

}
