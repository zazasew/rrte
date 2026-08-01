# Currency reliability

The primary currency reader queries five independent online sources in parallel and chooses the two successful quotes with the smallest relative spread, then averages them.

Sources:
1. Yahoo Finance - intraday market quote
2. Norges Bank - official NOK reference rates
3. European Central Bank - official euro reference rates
4. Frankfurter / ECB - API access to ECB reference data
5. ExchangeRate-API Open Access - independent reference feed

The dashboard now shows:
- rate with target currency, e.g. `11.82 NOK`
- daily/24h change when a source provides it
- last update time
- number of sources and the spread between the two consensus sources

Important: ECB and Norges Bank rates are official **reference rates**, normally published daily. They are not tick-by-tick market prices. The intraday source is used when available, while the multi-source consensus protects against a stale or broken provider.
