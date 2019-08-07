// $Id: MessageTags.java 637 2011-02-09 17:04:58Z yangwang $

package BFT.messages;

public class MessageTags {

    final public static int ClientRequest = 1;
    final public static int ReleaseCP = 3;
    final public static int Retransmit = 4;
    final public static int LoadCPMessage = 5;
    final public static int LastExecuted = 6;
    final public static int CPLoaded = 14;
    final public static int CPTokenMessage = 7;
    final public static int Reply = 8;
    final public static int WatchReply = 15;
    final public static int SignedRequestCore = 9;
    final public static int RequestCP = 10;
    final public static int CommittedNextBatch = 13;
    final public static int TentativeNextBatch = 12;
    final public static int SpeculativeNextBatch = 11;
    final public static int ForwardReply = 29;


    final public static int ReadOnlyRequest = 16;
    final public static int ReadOnlyReply = 17;

    final public static int FilteredRequestCore = 18;
    final public static int FilteredRequest = 19;
    final public static int FilteredBatchSuggestion = 28;

    final public static int BatchCompleted = 20;
    final public static int FetchCommand = 21;
    final public static int ForwardCommand = 22;
    final public static int FetchDenied = 23;
    final public static int CPUpdate = 24;
    final public static int CacheCommand = 25;

    final public static int OldRequestMessage = 26;
    final public static int BatchSuggestion = 27;


    final public static int NoOP = 5352;
    final public static int Verify = 5353;
    final public static int VerifyResponse = 5354;
    final public static int ExecViewChange = 5355;


    // message field sizes in bytes
    final public static int uint64Size = 8;
    final public static int uint32Size = 4;
    final public static int uint16Size = 2;

}
