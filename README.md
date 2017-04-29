# glossary
To generate glossary from any english text with translation to russian.

A few simple steps to have fun:
1. Create API key: https://tech.yandex.com/keys/?service=dict (note that there is a limit: 10000 request per day)
2. In order to avoid unintentional key sharing, do as follows:
  ```bash
  export DICT_API_KEY=<api_key_value>
  ```
  If it doesn't work for you: in root project directory, just create file with name **key** and place the api key into it.
3. Create a file with name **text.txt** and copy & past your text that you want to be "glossaried".
4. Run script using sbt: `sbt "runMain EntryPoint"` (or in any other way you like).
5. After a few seconds you can see a file **glossary.xlsx** with in the root directory.
6. Open generated file in Exel and sort lines by the first (A) cell.

"adjective" => "adj."
"noun" => "n."
"verb" => "v."
"adverb" => "adv."
"participle" => "p."
"pronoun" => "pron."
"conjunction" => "conj."
"preposition" => "prep."
"numeral" => "num."
