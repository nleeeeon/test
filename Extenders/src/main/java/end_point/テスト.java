package end_point;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class テスト {
  static final Gson gson = new Gson();
  static final HttpClient client = HttpClient.newHttpClient();
  static final String OLLAMA_URL = "http://localhost:11434/api/generate";
  static final String MODEL = "llama3.2:1b"; // そのままでもOK。軽いモデルなら他でも可

  public static class WordResult{
	  String topic;
	  String answer;
	  public WordResult(String topic,String answer) {
		  this.topic = topic;
		  this.answer = answer;
	  }
	  String getToic() {return this.topic;}
	  String getAnswer() {return this.answer;}
  }
  public static WordResult execution() throws Exception {
    JsonObject obj = generateTopicAnswerWithRetry(3);
    String topic  = safeString(obj.get("topic"));
    String answer = safeString(obj.get("answer"));

    System.out.println("topic:  " + topic);
    System.out.println("answer: " + answer);
    return new WordResult(topic, answer);
  }

  // --- 生成（最大 retries 回の自己修復つき） ---
  static JsonObject generateTopicAnswerWithRetry(int retries) throws Exception {
    JsonObject out = generateOnce(basePrompt(), null);
    for (int i = 0; i < retries; i++) {
      String err = validate(out);
      if (err == null) return out;                // OK
      out = generateOnce(fixPrompt(), out.toString()); // 壊れた出力を渡して修正依頼
    }
    String err = validate(out);
    if (err != null) throw new IllegalStateException("修正不能: " + err + " 出力: " + out);
    return out;
  }

  // --- 1回呼び出し（format:json で純JSON狙い） ---
  static JsonObject generateOnce(String prompt, String brokenOrNull) throws Exception {
    String system = "あなたは日本語の『お題作成AI』。出力は必ずJSONオブジェクト1件のみ。余計な文字や配列・説明は禁止。";
    String user = (brokenOrNull == null) ? prompt
      : "次の壊れた出力を、指示のJSONスキーマに完全準拠させて**1件のみ**返してください。\n"
        + "[壊れた出力]\n" + brokenOrNull + "\n[ここまで]\n\n" + prompt;

    String body = """
    {
      "model": %s,
      "format": "json",
      "system": %s,
      "prompt": %s,
      "options": {"temperature": 0.6, "top_p": 0.9, "num_predict": 80},
      "stream": false
    }
    """.formatted(j(MODEL), j(system), j(user));

    HttpRequest req = HttpRequest.newBuilder()
        .uri(URI.create(OLLAMA_URL))
        .header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
        .build();

    HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

    JsonObject outer = gson.fromJson(res.body(), JsonObject.class);
    String responseText = extractResponseText(outer.get("response"));  // 文字列 or 配列 対応

    // 壊れやすいので {…} だけ抜き出してパース（不完全JSON対策）
    JsonObject obj = safeParseJson(responseText);
    if (obj == null) throw new IllegalStateException("JSONで受け取れません: " + responseText);
    return obj;
  }

  // --- プロンプト（topic/answerのみ・配列禁止を強調） ---
  static String basePrompt() {
    return """
出力は**JSONオブジェクト1件のみ**（配列・説明・前置き・後置きは禁止）:
{"topic":"<お題の短いヒント>","answer":"<正解(名詞1語・10文字以内)>"}

厳守:
- 日本語。学生向け。公序良俗・差別・暴力・個人情報は不可。
- "answer" は**名詞1語**・**10文字以内**（例:「沖縄」「富士山」「将棋」「コアラ」）。抽象語・方法論・文章は禁止。
- 英単語・アルファベット・空白を含むものは禁止。
- "topic" は**1〜30文字**の短いヒント。正解を直書きしない。
- **配列やオブジェクトのネストは禁止**。2キーのみ: topic, answer。
- 例（良い）: {"topic":"○○に関する簡単なヒント","answer":"△△"}
- 例（悪い/禁止）: {"topic":"抽象的・長文な説明","answer":"文章や方法論"} / {"topic":[...]} / {"difficulty":"easy"} など
""";
  }

  static String fixPrompt() {
    return """
上記の壊れた出力を、次の条件で**JSONオブジェクト1件**に修正して返してください:
- 2キーのみ: topic, answer
- answerは**名詞1語**・10文字以内。抽象語/方法論/文章は禁止
- topicは30文字以内の短いヒント。正解を直書きしない
- **配列・説明・追加キーすべて禁止**
出力はJSONオブジェクト**のみ**
""";
  }

  // --- バリデーション（topic/answer だけを厳しく確認） ---
  static String validate(JsonObject o) {
    if (o == null) return "空JSON";
    if (!o.has("topic"))  return "topic欠落";
    if (!o.has("answer")) return "answer欠落";
    String topic  = safeString(o.get("topic"));
    String answer = safeString(o.get("answer"));

    if (topic == null || topic.isBlank() || topic.length() > 30) return "topic不正";
    // 1語・10文字以内（空白を含まない）
    if (answer == null || !answer.matches("^[^\\p{Z}\\s]{1,10}$")) return "answer不正(名詞1語/<=10)";
    return null;
  }

  // --- ヘルパー群 ---
  static String extractResponseText(JsonElement resp) {
    if (resp == null || resp.isJsonNull()) return "";
    if (resp.isJsonPrimitive()) return resp.getAsString();
    if (resp.isJsonArray()) {
      StringBuilder sb = new StringBuilder();
      for (JsonElement e : resp.getAsJsonArray()) {
        sb.append(e.isJsonPrimitive() ? e.getAsString() : e.toString());
      }
      return sb.toString();
    }
    return resp.toString();
  }

  static JsonObject safeParseJson(String raw) {
    if (raw == null) return null;
    String s = raw.trim();
    int l = s.indexOf('{'), r = s.lastIndexOf('}');
    if (l >= 0 && r > l) {
      String core = s.substring(l, r + 1);
      try { return JsonParser.parseString(core).getAsJsonObject(); }
      catch (Exception e) { System.err.println("JSONパース失敗: " + e + "\n入力=" + core); }
    } else {
      System.err.println("JSONっぽい部分なし: " + raw);
    }
    return null;
  }

  static String safeString(JsonElement e){
    if (e == null || e.isJsonNull()) return null;
    if (e.isJsonPrimitive()) return e.getAsString();
    if (e.isJsonArray()) {
      JsonArray arr = e.getAsJsonArray();
      if (arr.size() == 0) return "";
      JsonElement f = arr.get(0);
      return f.isJsonPrimitive() ? f.getAsString() : f.toString();
    }
    return e.toString();
  }

  static String j(String s){
    return "\"" + s.replace("\\","\\\\").replace("\"","\\\"").replace("\n","\\n") + "\"";
  }
}
