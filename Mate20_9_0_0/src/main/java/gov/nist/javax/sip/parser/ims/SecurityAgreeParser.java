package gov.nist.javax.sip.parser.ims;

import gov.nist.javax.sip.header.SIPHeader;
import gov.nist.javax.sip.header.SIPHeaderList;
import gov.nist.javax.sip.header.ims.SecurityAgree;
import gov.nist.javax.sip.header.ims.SecurityClient;
import gov.nist.javax.sip.header.ims.SecurityClientList;
import gov.nist.javax.sip.header.ims.SecurityServer;
import gov.nist.javax.sip.header.ims.SecurityServerList;
import gov.nist.javax.sip.header.ims.SecurityVerify;
import gov.nist.javax.sip.header.ims.SecurityVerifyList;
import gov.nist.javax.sip.parser.HeaderParser;
import gov.nist.javax.sip.parser.Lexer;
import java.text.ParseException;

public class SecurityAgreeParser extends HeaderParser {
    public SecurityAgreeParser(String security) {
        super(security);
    }

    protected SecurityAgreeParser(Lexer lexer) {
        super(lexer);
    }

    protected void parseParameter(SecurityAgree header) throws ParseException {
        if (debug) {
            dbg_enter("parseParameter");
        }
        try {
            header.setParameter(nameValue('='));
        } finally {
            if (debug) {
                dbg_leave("parseParameter");
            }
        }
    }

    public SIPHeaderList parse(SecurityAgree header) throws ParseException {
        SIPHeaderList list;
        if (header.getClass().isInstance(new SecurityClient())) {
            list = new SecurityClientList();
        } else if (header.getClass().isInstance(new SecurityServer())) {
            list = new SecurityServerList();
        } else if (!header.getClass().isInstance(new SecurityVerify())) {
            return null;
        } else {
            list = new SecurityVerifyList();
        }
        this.lexer.SPorHT();
        this.lexer.match(4095);
        header.setSecurityMechanism(this.lexer.getNextToken().getTokenValue());
        this.lexer.SPorHT();
        char la = this.lexer.lookAhead(0);
        if (la == 10) {
            list.add((SIPHeader) header);
            return list;
        }
        SIPHeader header2;
        if (la == ';') {
            this.lexer.match(59);
        }
        this.lexer.SPorHT();
        while (this.lexer.lookAhead(0) != 10) {
            try {
                parseParameter(header2);
                this.lexer.SPorHT();
                char laInLoop = this.lexer.lookAhead(0);
                if (laInLoop == 10) {
                    break;
                } else if (laInLoop == 0) {
                    break;
                } else {
                    if (laInLoop == ',') {
                        list.add(header2);
                        if (header2.getClass().isInstance(new SecurityClient())) {
                            header2 = new SecurityClient();
                        } else if (header2.getClass().isInstance(new SecurityServer())) {
                            header2 = new SecurityServer();
                        } else if (header2.getClass().isInstance(new SecurityVerify())) {
                            header2 = new SecurityVerify();
                        }
                        this.lexer.match(44);
                        this.lexer.SPorHT();
                        this.lexer.match(4095);
                        header2.setSecurityMechanism(this.lexer.getNextToken().getTokenValue());
                    }
                    this.lexer.SPorHT();
                    if (this.lexer.lookAhead(0) == ';') {
                        this.lexer.match(59);
                    }
                    this.lexer.SPorHT();
                }
            } catch (ParseException ex) {
                throw ex;
            }
        }
        list.add(header2);
        return list;
    }
}
