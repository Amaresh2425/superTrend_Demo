package com.example.superTrendDemo;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

public class Test {

	public static void main(String[] args) {
		// TODO Auto-generated method stub
		 String dateStr = "2023-02-15T09:15:00+0530";
	        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssZ");
	        ZonedDateTime zonedDateTime = ZonedDateTime.parse(dateStr, formatter);
	        System.out.println(zonedDateTime);
	}

}
