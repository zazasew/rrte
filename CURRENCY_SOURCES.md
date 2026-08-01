# Currency data strategy

The app now queries five sources in parallel for every configured fiat pair:

1. Yahoo Finance — intraday market quote where available.
2. Norges Bank — official Norwegian central-bank reference rate.
3. European Central Bank — official EUR reference rate.
4. Frankfurter — API access to central-bank reference data.
5. ExchangeRate-API Open Access — independent daily reference feed.

The app selects the two successful sources whose rates have the smallest relative
spread and averages those two values. It also records the source names internally.

This is deliberately a consensus strategy rather than trusting Yahoo alone.
Important: central-bank/reference feeds are daily, not tick-by-tick. The only
intraday source in this keyless setup is Yahoo Finance. If you need true bank-
quality streaming FX, a licensed provider (for example a bank/market-data feed)
requires an API agreement or backend credentials.

ExchangeRate-API's Open Access endpoint is documented as once-daily and requires
attribution, so the app includes the provider attribution in the Settings/about
area rather than treating it as a live tick source.
