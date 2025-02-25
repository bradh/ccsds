/*
 *   Copyright (c) 2021 Dario Lucia (https://www.dariolucia.eu)
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package eu.dariolucia.ccsds.viewer.utils;

import com.beanit.jasn1.ber.types.*;
import com.beanit.jasn1.ber.types.string.BerVisibleString;

import javax.xml.bind.DatatypeConverter;
import java.util.Arrays;

public class SlePduAttribute {

	private final String name;
	private final String type;
	private final Object value;
	
	public SlePduAttribute(String name, String type, Object value) {
		this.name = name;
		this.type = type;
		this.value = value;
	}
	
	public final String getName() {
		return name;
	}
	
	public final String getType() {
		return type;
	}
	
	public final Object getValue() {
		return value;
	}

	public String getValueAsString() {
		if(value == null || value instanceof BerNull) {
			return "<NULL>";
		}
		if(value instanceof BerBoolean) {
			return String.valueOf(((BerBoolean) value).value);
		}
		if(value instanceof BerEnum) {
			return String.valueOf(((BerEnum) value).value.intValue());
		}
		if(value instanceof BerInteger) {
			return String.valueOf(((BerInteger) value).value.intValue());
		}
		if(value instanceof BerVisibleString) {
			return new String(((BerVisibleString) value).value);
		}
		if(value instanceof BerOctetString) {
			return DatatypeConverter.printHexBinary(((BerOctetString) value).value);
		}
		if(value instanceof BerObjectIdentifier) {
			return Arrays.toString(((BerObjectIdentifier) value).value);
		}
		if(value instanceof BerReal) {
			return String.valueOf(((BerReal) value).value);
		}
		if(value instanceof byte[]) {
			return DatatypeConverter.printHexBinary(((byte[]) value));
		}
		if(value instanceof String) {
			return value.toString();
		}
		if(value instanceof Number) {
			return value.toString();
		}
		return "";
	}

}
