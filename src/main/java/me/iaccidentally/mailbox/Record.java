package me.iaccidentally.mailbox;


public class Record
{
	private String player;
	private int time;

	public Record(String player, int time)
	{
		this.player = player;
		this.time = time;
	}

	public String getPl()
	{
		return this.player;
	}

	public void setPl(String player)
	{
		this.player = player;
	}

	public int getTime()
	{
		return this.time;
	}

	public void setTime(int time)
	{
		this.time = time;
	}
}