# shepard releases

This project can be used to create automatic releases with release notes.

## Preparation (only required once)

1. go to [gitlab.com](https://gitlab.com/-/profile/personal_access_tokens) and add a personal access token with `api` permissions
2. create a file `token.txt` and add your personal access token there
3. create a virtual environment by running `python -m venv venv`.
4. activate your newly created virtual environment using `.\venv\Scripts\Activate.ps1` (Windows) or using `source .\venv\Scripts\activate` (Linux)
5. install the required packages by using `pip install -r .\requirements.txt`.

## Usage

1. activate the virtual environment using `.\venv\Scripts\Activate.ps1` (Windows) or using `source .\venv\Scripts\activate` (Linux)
2. run the script by using `python main.py`.
3. fill in all the required information and create the release.
