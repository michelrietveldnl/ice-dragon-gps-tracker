package com.stonedonkey.icedragon;

import java.io.File;
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;


import android.app.AlertDialog;
import android.app.ListActivity;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

public class FileBrowserActivity extends ListActivity implements OnClickListener {

	private ArrayList<String> items;
	private String path = "/";
	private File f;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setContentView(R.layout.file_browser);

		final Bundle extras = this.getIntent().getExtras();
		if (extras != null) {
			String startPath = extras.getString("StartPath");
			if (startPath != null)
				path = startPath;
		}

		bindFolders();

		Button b = (Button) findViewById(R.id.buttonSelectPath);
		b.setOnClickListener(this);

		b = (Button) findViewById(R.id.buttonCreateFolder);
		b.setOnClickListener(this);

	}

	private void bindFolders() {

		try {
			f = new File(path);

			FilenameFilter filter = new FilenameFilter() {
				@Override
				public boolean accept(File dir, String filename) {
					File sel = new File(dir, filename);
					return sel.isDirectory();
				}
			};

			String[] files = f.list(filter);
			if (files != null)
				items = new ArrayList<String>(Arrays.asList(files));
			else
				items = new ArrayList<String>();

			ListView lv = getListView();
			lv.removeAllViewsInLayout();

			if (items != null) {
				Collections.sort(items,String.CASE_INSENSITIVE_ORDER);
				ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, R.layout.file_browser_row, items);
				lv.setAdapter(adapter);
			}

			if (path != null && path.length() > 1)
				items.add(0, "..");

			TextView currentPath = (TextView) findViewById(R.id.textViewFilePath);
			currentPath.setText(path);
		}
		catch (Exception ex) {
			Toast.makeText(this, "Error changing to folder.", Toast.LENGTH_SHORT).show();
		}
	}

	@Override
	protected void onListItemClick(ListView l, View v, int position, long id) {
		super.onListItemClick(l, v, position, id);

		if (position == 0 && items.get(position).equals("..")) {
			path = f.getParentFile().toString();
			if (!path.equals("/"))
				path = path + "/";
		}
		else {
			path = path + items.get(position) + "/";

		}

		bindFolders();

	}

	@Override
	public void onClick(View v) {
		switch (v.getId())
		{
		case R.id.buttonSelectPath:
		{
			Intent returnIntent = new Intent();
			returnIntent.putExtra("path", path);
			setResult(RESULT_OK, returnIntent);
			finish();
			return;
		}
		case R.id.buttonCreateFolder:
		{
			CreateNewDirectory();
			return;
		}
		}
	}

	private void CreateNewDirectory() {

		final EditText input = new EditText(this);
		input.setSingleLine(true);

		new AlertDialog.Builder(this).setTitle("Create Directory").setMessage("Enter name of new directory:").setView(input).setPositiveButton("Create", new DialogInterface.OnClickListener() {

			@Override
			public void onClick(DialogInterface dialog, int which) {
				String folderName = input.getText().toString();

				if (folderName == null || folderName.length() == 0) {
					Toast.makeText(getApplicationContext(), "Invalid or missing filename", Toast.LENGTH_SHORT).show();
					return;
				}
				boolean result = true;
				try {
					result = new File(path + folderName + "/").mkdirs();
					bindFolders();
				}
				catch (Exception ex) {
					result = false;
				}

				if (result)
					Toast.makeText(getApplicationContext(), "Directory was created successfully.", Toast.LENGTH_SHORT).show();
				else
					Toast.makeText(getApplicationContext(), "There was an error creating the directory.", Toast.LENGTH_SHORT).show();

			}
		}).setNegativeButton("Cancel", new DialogInterface.OnClickListener() {

			@Override
			public void onClick(DialogInterface dialog, int which) {

			}
		}).show();
	}

}
