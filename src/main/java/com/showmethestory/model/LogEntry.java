package com.showmethestory.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * A structured log entry broadcast via SSE to the frontend.
 */
public class LogEntry {

    private String level;
    private String msg;

    @JsonProperty("msg_en")
    private String msgEn;

    @JsonProperty("msg_key")
    private String msgKey;

    @JsonProperty("msg_args")
    private List<String> msgArgs;

    private String time;

    public LogEntry() {}

    public String getLevel() { return level; }
    public void setLevel(String level) { this.level = level; }

    public String getMsg() { return msg; }
    public void setMsg(String msg) { this.msg = msg; }

    public String getMsgEn() { return msgEn; }
    public void setMsgEn(String msgEn) { this.msgEn = msgEn; }

    public String getMsgKey() { return msgKey; }
    public void setMsgKey(String msgKey) { this.msgKey = msgKey; }

    public List<String> getMsgArgs() { return msgArgs; }
    public void setMsgArgs(List<String> msgArgs) { this.msgArgs = msgArgs; }

    public String getTime() { return time; }
    public void setTime(String time) { this.time = time; }
}
