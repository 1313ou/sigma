/*
  @(#)SimpleDOMParser.java
 * From DevX
 * http://www.devx.com/xml/Article/10114
 * Further modified for Articulate Software by Adam Pease 12/2005
 */

package com.articulate.sigma.io;

import java.io.Serializable;
import java.util.*;

/**
 * SimpleElement is the only node type for
 * simplified DOM model.  Note that all CDATA values are stored with
 * reserved any characters '>' '<' converted to &gt; and &lt;
 * respectively.
 */
public class SimpleElement implements Serializable
{

	private final String tagName;
	private String text;
	private final Map<String, String> attributes;
	private final List<SimpleElement> childElements;

	public SimpleElement(String tagName)
	{
		this.tagName = tagName;
		attributes = new HashMap<>();
		childElements = new ArrayList<>();
	}

	public String getTagName()
	{
		return tagName;
	}

	public String getText()
	{
		if (text != null && !text.isEmpty())
			return SimpleDOMParser.convertToReservedCharacters(text);
		else
			return text;
	}

	public void setText(String text)
	{
		if (text != null && !text.isEmpty())
			this.text = SimpleDOMParser.convertFromReservedCharacters(text.trim());
		else
			this.text = text;
	}

	public String getAttribute(String name)
	{
		String attribute = attributes.get(name);
		if (attribute != null && !attribute.isEmpty())
			return SimpleDOMParser.convertToReservedCharacters(attribute);
		else
			return attribute;
	}

	public Set<String> getAttributeNames()
	{
		return attributes.keySet();
	}

	public void setAttribute(String name, String value)
	{
		if (value != null && !value.isEmpty())
			value = SimpleDOMParser.convertFromReservedCharacters(value);
		attributes.put(name, value);
	}

	public void addChildElement(SimpleElement element)
	{
		childElements.add(element);
	}

	public List<SimpleElement> getChildElements()
	{
		return childElements;
	}

	/**
	 * String form
	 */
	public String toString(int indent, boolean forFile)
	{
		StringBuilder strIndent = new StringBuilder();
		for (int i = 0; i < indent; i++)
		{
			strIndent.append("  ");
		}
		StringBuilder result = new StringBuilder();
		result.append(strIndent.toString()).append("<").append(getTagName()).append(" ");
		Set<String> names = new HashSet<>(getAttributeNames());
		for (String name : names)
		{
			String value = getAttribute(name);
			if (forFile)
				value = SimpleDOMParser.convertFromReservedCharacters(value);
			result.append(name).append("=\"").append(value).append("\" ");
		}
		List<SimpleElement> children = getChildElements();
		if (children.size() == 0 && (getText() == null || getText().equals("null")))
			result.append("/>\n");
		else
		{
			result.append(">\n");
			if (getText() != null && !getText().isEmpty() && !getText().equals("null"))
			{
				if (forFile)
					result.append(SimpleDOMParser.convertFromReservedCharacters(getText()));
				else
					result.append(getText());
				result.append("\n");
			}
			for (SimpleElement element : children)
			{
				result.append(element.toString(indent + 1, forFile));
			}
			result.append(strIndent.toString()).append("</").append(getTagName()).append(">\n");
		}

		return result.toString();
	}

	/**
	 * String form
	 */
	public String toString()
	{
		return toString(0, false);
	}
}
