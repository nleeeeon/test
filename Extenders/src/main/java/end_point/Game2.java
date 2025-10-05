package end_point;                         // このクラスが属するパッケージ名


import java.io.IOException;              // sendText 時などの入出力例外
// List/Map など汎用コレクション
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap; // 併走アクセスに強いMap
import java.util.concurrent.CopyOnWriteArrayList; // スレッドセーフなList

import javax.websocket.CloseReason;
import javax.websocket.OnClose;
import javax.websocket.OnError;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.ServerEndpoint;
/**
 * 3人入るごとにコホート（組）を作成し、
 * 2人を PAIR（同画面）、1人を SOLO（別画面）に自動割り当てる。
 * エンドポイントは ws://localhost:8080/<コンテキスト>/ws
 */
@ServerEndpoint("/Game2")                   // このクラスが /ws でWebSocketを受ける宣言
public class Game2 {

    // 全接続（単一ルーム前提の簡易管理）
    private static final List<Session> ALL = new CopyOnWriteArrayList<>();
    // 各セッションID -> どのコホートIDに属するか
    private static final Map<String, Integer> COHORT = new ConcurrentHashMap<>();
    // 各セッションID -> 役割（"PAIR" or "SOLO"）
    private static final Map<String, String> ROLE = new ConcurrentHashMap<>();
    // コホートID -> そのコホートに属するセッションID一覧
    private static final Map<Integer, List<String>> COHORT_MEMBERS = new ConcurrentHashMap<>();
    private static volatile int cohortCounter = 1; // 次に作るコホートのID（1,2,3,...）。volatileで可視性確保

    @OnOpen                                  // クライアント接続時に呼ばれる
    public void onOpen(Session session) throws IOException {
        System.out.println(session.getId()+"が来ました");
    	ALL.add(session);                     // 全体リストに追加
System.out.println(ALL.size()+"これだよ");
broadcastCount();
        /*if (ALL.size() >= 3) {            // 3人揃ったらコホート発足
        	System.out.println("ゲームスタート");
        	word_guessing();
        }*/
    }

    @OnMessage                               // クライアントからの受信時に呼ばれる
    public void onMessage(Session session, String message) throws Exception {
    	switch (message.substring(0, 2)) {
        /*case "un": // ユーザ名の登録
            user_registration(session, message.substring(2));
            return;*/
        case "wo": //ワード推測のゲームを開始
        	for (Session sid : ALL) {
                sendText(findById(sid.getId()), "haide");  // ターゲットへ原文転送
            }
            word_guessing();
            return;
        }

        if (message.startsWith("{")) {        // JSONっぽいなら
            boolean toPair = message.contains("\"type\":\"pairSync\""); // PAIR向け？
            boolean toSolo = message.contains("\"type\":\"soloSync\""); // SOLO向け？

            if(toSolo){
                for (Session sid : ALL) {
                	System.out.println(message);
                	if(message.equals("{\"type\":\"soloSync\",\"payload\":\""+SECRET_WORD+"\"}")) {
                		
                		sendText(findById(sid.getId()), "clear");  // ターゲットへ原文転送
                	}else {
                		sendText(findById(sid.getId()), "bad");  // ターゲットへ原文転送
                	}
                }
                ALL.clear();
                return;
            }
            
            // ここでは送信元も含めて配信（好みで除外しても良い）
            for (Session sid : ALL) {
                sendText(findById(sid.getId()), message);  // ターゲットへ原文転送
            }
            return;
        }
        Integer cohortId = COHORT.get(session.getId()); // 自分のコホートID
        if (cohortId == null) {               // まだ割り当て前なら
            sendText(session, json("error", Map.of("msg", "not_assigned")));
            return;
        }
        String myRole = ROLE.get(session.getId()); // 自分の役割
        if (myRole == null) myRole = "PAIR";       // 念のためデフォルト

        // 超簡易プロトコル：JSON文字列に "type":"pairSync" / "soloSync" が含まれているかで振分
        if (message.startsWith("{")) {        // JSONっぽいなら
            boolean toPair = message.contains("\"type\":\"pairSync\""); // PAIR向け？
            boolean toSolo = message.contains("\"type\":\"soloSync\""); // SOLO向け？

            List<String> targets = new ArrayList<>();
            for (String sid : COHORT_MEMBERS.getOrDefault(cohortId, List.of())) {
                String r = ROLE.get(sid);     // 相手の役割を参照
                if (toPair && "PAIR".equals(r)) targets.add(sid); // PAIR宛収集
                if (toSolo && "SOLO".equals(r)) targets.add(sid); // SOLO宛収集
            }
            // ここでは送信元も含めて配信（好みで除外しても良い）
            for (String sid : targets) {
                sendText(findById(sid), message);  // ターゲットへ原文転送
            }
        } else {
            // ただのテキストは同一コホート全員へ一斉配信
            broadcastToCohort(cohortId, message);
        }
    }

    @OnClose                                  // 切断時
    public void onClose(Session session, CloseReason reason) {
        cleanup(session);                      // 各種マップから削除
        broadcastCount();
    }

    @OnError                                  // エラー時
    public void onError(Session session, Throwable t) {
        cleanup(session);                      // とりあえず同様に掃除
    }

    // --------- helpers（内部ユーティリティ） ---------

    private static List<Session> getWaitingSessions() {
        // まだCOHORTに登録されていない = コホート未割当の接続を抽出
        List<Session> waiting = new ArrayList<>();
        for (Session s : ALL) {
            if (!COHORT.containsKey(s.getId())) waiting.add(s);
        }
        return waiting;
    }

    private static Session findById(String id) {
        // セッションIDから Session オブジェクトを見つける
        for (Session s : ALL) {
            if (s.getId().equals(id)) return s;
        }
        return null;                           // 見つからなければ null
    }

    private static void sendText(Session s, String text) throws IOException {
        // セッションが開いていればテキスト送信
        if (s != null && s.isOpen()) s.getBasicRemote().sendText(text);
    }

    private static void broadcastToCohort(int cohortId, String text) throws IOException {
        // 指定コホートの全メンバーに一斉送信
        for (String sid : COHORT_MEMBERS.getOrDefault(cohortId, List.of())) {
            sendText(findById(sid), text);
        }
    }

    private static String json(String type, Map<String, Object> kv) {
        // 依存ゼロで超簡易JSONを生成（実運用は Jackson/Gson 推奨）
        StringBuilder sb = new StringBuilder();
        sb.append("{\"type\":\"").append(type).append("\""); // "type"フィールド
        for (Map.Entry<String, Object> e : kv.entrySet()) {
            sb.append(",\"").append(e.getKey()).append("\":"); // 各キー
            Object v = e.getValue();
            if (v instanceof Number || v instanceof Boolean) {
                sb.append(v);                   // 数値/真偽は裸で
            } else {
                sb.append("\"").append(String.valueOf(v).replace("\"","\\\"")).append("\"");
                // 文字列はクォート＆"のエスケープ
            }
        }
        sb.append("}");
        return sb.toString();                   // 完成したJSON文字列
    }

    private static void cleanup(Session session) {
        // 接続終了等で呼ばれるクリーンアップ
        ALL.remove(session);                    // 全体リストから除外
        String sid = session.getId();
        Integer cohortId = COHORT.remove(sid);  // コホート紐付け解除
        ROLE.remove(sid);                       // 役割もクリア
        if (cohortId != null) {
            List<String> list = COHORT_MEMBERS.get(cohortId); // コホートの名簿から削除
            if (list != null) {
                list.remove(sid);
                if (list.isEmpty()) {          // 誰もいなければエントリごと削除
                    COHORT_MEMBERS.remove(cohortId);
                }
            }
        }
    }
    private static void word_guessing() throws Exception{
        if (ALL.size() >= 3) {            // 3人揃ったらコホート発足
            int myCohort = cohortCounter++;   // 新しいコホートIDを採番
            List<String> ids = new ArrayList<>();
            for (int i = 0; i < ALL.size(); i++) {
                Session s = ALL.get(i);   // 待機者を順に取り出し
                ids.add(s.getId());           // セッションIDを控える
                COHORT.put(s.getId(), myCohort); // セッション -> コホートID の紐付け
            }
            COHORT_MEMBERS.put(myCohort, ids); // コホート -> メンバー一覧 を登録


            Random random = new Random();
            int randomIndex = random.nextInt(ALL.size());

            // 役割付与：先頭2人を PAIR、最後の1人を SOLO にする（単純な方針）
            for(int i = 0;i < ids.size();i++){
                if(randomIndex == i){
                    ROLE.put(ids.get(i), "SOLO");
                }else {
                    ROLE.put(ids.get(i), "PAIR");
                }
            }

            // 3人に割当結果を通知（JSON文字列を手組み）
            randomIndex = random.nextInt(word.length);
            SECRET_WORD = word[randomIndex];
            for (String sid : ids) {
                String role = ROLE.get(sid);
                Map<String, Object> payload = new HashMap<>();
                                payload.put("cohort", myCohort);
                               payload.put("role", role);
                               payload.put("ans", SECRET_WORD);
                                if ("PAIR".equals(role)) {
                                    payload.put("word", SECRET_WORD); // ★PAIRにだけお題を同報
                                }
                                sendText(findById(sid), json("assign", payload));
            }
        }
    }
    static String randomIndex;
    static String word[] = {"りんご","車","学校","先生","本","パソコン","犬","猫","家","電車","海","山","空","鳥","音楽","映画","花","時計","机","椅子","スマートフォン","ゲーム","料理","雨","太陽","月","星","風","川","橋","道","信号","カメラ","病院","公園","写真","友達",
    		"家族","仕事","旅行","勉強","文字","言葉","歌","絵","ニュース","店","食べ物","飲み物","時間","夢"};
    static String SECRET_WORD;
    private static void broadcastCount() {
    	        String msg = json("count", Map.of(
    	                "total", ALL.size(),
    	                "waiting", getWaitingSessions().size()
    	        ));
    	        for (Session s : ALL) {
    	            try { sendText(s, msg); } catch (IOException ignored) {}
    	        }
    	    }
    
}







/*追加コード
private static Map<String, String> userNames = new HashMap<>();

private static void user_registration(Session session, String username)[
    userNames.add(session.getId,username);
    System.out.println(username+"を登録しました");//デバッグ用
]*/




    
/*以下のメソッドはユーザーが入ったときに先に入ったユーザーに後何人かの情報を更新するコード。
private static void notifyWaitingAll() {
    List<Session> waiting = getWaitingSessions();   // まだCOHORT未割当
    int need = Math.max(0, 3 - waiting.size());
    String msg = json("waiting", Map.of("need", need));
    for (Session s : waiting) {
        try { sendText(s, msg); } catch (IOException ignored) {}
    }
}*/




