        // MESSAGE V2_EXTENSION PACKING
        package com.MAVLink.common;
        
        import com.MAVLink.Messages.MAVLinkMessage;
        import com.MAVLink.Messages.MAVLinkPacket;
        import com.MAVLink.Messages.MAVLinkPayload;
        //import android.util.Log;
        
        /**
        * Message implementing parts of the V2 payload specs in V1 frames for transitional support.
        */
        public class msg_v2_extension extends MAVLinkMessage{
        
        public static final int MAVLINK_MSG_ID_V2_EXTENSION = 248;
        public static final int MAVLINK_MSG_LENGTH = 254;
        private static final long serialVersionUID = MAVLINK_MSG_ID_V2_EXTENSION;
        
        
         	/**
        * A code that identifies the software component that understands this message (analogous to usb device classes or mime type strings).  If this code is less than 32768, it is considered a 'registered' protocol extension and the corresponding entry should be added to https://github.com/mavlink/mavlink/extension-message-ids.xml.  Software creators can register blocks of message IDs as needed (useful for GCS specific metadata, etc...). Message_types greater than 32767 are considered local experiments and should not be checked in to any widely distributed codebase.
        */
        public short message_type;
         	/**
        * Network ID (0 for broadcast)
        */
        public byte target_network;
         	/**
        * System ID (0 for broadcast)
        */
        public byte target_system;
         	/**
        * Component ID (0 for broadcast)
        */
        public byte target_component;
         	/**
        * Variable length payload. The length is defined by the remaining message length when subtracting the header and other fields.  The entire content of this block is opaque unless you understand any the encoding message_type.  The particular encoding used can be extension specific and might not always be documented as part of the mavlink specification.
        */
        public byte pPayload[] = new byte[249];
        
        
        /**
        * Generates the payload for a mavlink message for a message of this type
        * @return
        */
        public MAVLinkPacket pack(){
		MAVLinkPacket packet = new MAVLinkPacket();
		packet.len = MAVLINK_MSG_LENGTH;
		packet.sysid = 255;
		packet.compid = 190;
		packet.msgid = MAVLINK_MSG_ID_V2_EXTENSION;
        		packet.payload.putShort(message_type);
        		packet.payload.putByte(target_network);
        		packet.payload.putByte(target_system);
        		packet.payload.putByte(target_component);
        		 for (int i = 0; i < pPayload.length; i++) {
                    packet.payload.putByte(pPayload[i]);
                    }
        
		return packet;
        }
        
        /**
        * Decode a v2_extension message into this class fields
        *
        * @param payload The message to decode
        */
        public void unpack(MAVLinkPayload payload) {
        payload.resetIndex();
        	    message_type = payload.getShort();
        	    target_network = payload.getByte();
        	    target_system = payload.getByte();
        	    target_component = payload.getByte();
        	     for (int i = 0; i < payload.MAX_PAYLOAD_SIZE; i++) {
        	    	 pPayload[i] = payload.getByte();
                    }
        
        }
        
        /**
        * Constructor for a new message, just initializes the msgid
        */
        public msg_v2_extension(){
    	msgid = MAVLINK_MSG_ID_V2_EXTENSION;
        }
        
        /**
        * Constructor for a new message, initializes the message with the payload
        * from a mavlink packet
        *
        */
        public msg_v2_extension(MAVLinkPacket mavLinkPacket){
        this.sysid = mavLinkPacket.sysid;
        this.compid = mavLinkPacket.compid;
        this.msgid = MAVLINK_MSG_ID_V2_EXTENSION;
        unpack(mavLinkPacket.payload);
        //Log.d("MAVLink", "V2_EXTENSION");
        //Log.d("MAVLINK_MSG_ID_V2_EXTENSION", toString());
        }
        
                  
        /**
        * Returns a string with the MSG name and data
        */
        public String toString(){
    	return "MAVLINK_MSG_ID_V2_EXTENSION -"+" message_type:"+message_type+" target_network:"+target_network+" target_system:"+target_system+" target_component:"+target_component+" payload:"+pPayload+"";
        }
        }
        