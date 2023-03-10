import React, { useEffect, useState } from 'react';
import { Button, TextField, Grid, Typography, Container } from '@mui/material/';
import { createTheme, ThemeProvider } from '@mui/material/styles';
import UseAxios from '../../utils/UseAxios';
// import swal from 'sweetalert';
import swal2 from 'sweetalert2';
import { useNavigate } from 'react-router-dom';
import Header from '../../components/common/Header';

const theme = createTheme({
  palette: {
    primary: {
      main: '#FFC947',
    },
    secondary: {
      main: '#11cb5f',
    },
  },
  typography: {
    fontFamily: 'SCD400',
  },
});
function ChangePasswordPage() {
  const navigate = useNavigate();
  const [password, setPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [newPasswordCheck, setNewPasswordCheck] = useState('');
  const [canSubmit, setCanSubmit] = useState(false);
  const [userData, setUserData] = useState(false);

  useEffect(() => {
    UseAxios.get(`/users/profiles`).then((res) => {
      setUserData(res.data);
    });
  }, []);

  /*비밀번호 유효 검사 */
  const onChangePassword = (e) => {
    setPassword(e.target.value);
  };
  const onChangeNewPassword = (e) => {
    setNewPassword(e.target.value);
  };
  const newPasswordValidation = () => {
    let space = /[~!@#$%";'^,&*()_+|</>=>`?:{[\}]/;
    return !space.test(newPassword) && newPassword.length > 1;
  };
  /*비밀번호 확인 */

  const onChangeNewPasswordCheck = (e) => {
    setNewPasswordCheck(e.target.value);
  };
  const newPasswordCheckValidation = () => {
    return newPassword !== newPasswordCheck && newPasswordCheck.length > 1;
  };

  // let body = {
  //   password,
  //   newPassword,
  // };

  const onSubmit = (event) => {
    event.preventDefault();
    let valid = canSubmit;
    (async () => {
      if (newPassword.length < 8) {
        // swal('비밀번호는 8글자 이상이어야 합니다.');
        swal2.fire({
          title: '비밀번호는 8글자 이상이어야 합니다.',
          confirmButtonColor: '#ffc947',
          confirmButtonText: '확인',
        });
        setCanSubmit(false);
      } else if (newPassword !== newPasswordCheck) {
        // swal('비밀번호 확인이 일치하지 않습니다');
        swal2.fire({
          title: '비밀번호 확인이 일치하지 않습니다',
          confirmButtonColor: '#ffc947',
          confirmButtonText: '확인',
        });
        setCanSubmit(false);
      } else {
        setCanSubmit(true);
        valid = true;
      }
    })();

    if (valid) {
      UseAxios.put(`/users/change-password`, {
        password: password,
        newPassword: newPassword,
        userId: userData.id,
      })
        .then((res) => {
          // console.log(res);
          // swal('비밀번호 변경이 완료되었습니다.');
          swal2
            .fire({
              title: '비밀번호 변경이 완료되었습니다.',
              confirmButtonColor: '#ffc947',
              confirmButtonText: '확인',
            })
            .then((result) => {
              if (result.isConfirmed) navigate('/mypage', { state: true });
            });
        })
        .catch((err) => {
          swal2.fire({
            title: '현재 비밀번호가 일치하지 않습니다.',
            confirmButtonColor: '#ffc947',
            confirmButtonText: '확인',
          });
        });
    }
  };

  return (
    <div className='wrapper'>
      <ThemeProvider theme={theme}>
        <Header title={'비밀번호 변경'}></Header>
        <Container component='main' maxWidth='sm'>
          <form>
            <Grid container spacing={2}>
              <Grid item xs={12}>
                <Typography sx={{ ml: 1, mb: 0.5 }}>현재 비밀번호*</Typography>
                <TextField
                  variant='outlined'
                  required
                  fullWidth
                  type='password'
                  id='password'
                  name='password'
                  placeholder='현재 비밀번호'
                  onChange={onChangePassword}
                  size='small'
                  value={password}
                />
              </Grid>

              <Grid item xs={12}>
                <Typography sx={{ ml: 1, mt: 1.5, mb: 0.5 }}>새 비밀번호*</Typography>
                <TextField
                  variant='outlined'
                  required
                  fullWidth
                  type='password'
                  id='newPassword'
                  name='newPassword'
                  placeholder='새 비밀번호 입력'
                  size='small'
                  onChange={onChangeNewPassword}
                  error={newPasswordValidation()}
                  helperText={
                    newPasswordValidation()
                      ? '영문, 숫자, 특수문자를 조합해 8글자 이상 입력하세요'
                      : ''
                  }
                  value={newPassword}
                />
              </Grid>
              <Grid item xs={12}>
                <TextField
                  required
                  variant='outlined'
                  fullWidth
                  type='password'
                  id='newPasswordCheck'
                  name='newPasswordCheck'
                  placeholder='새 비밀번호 확인'
                  size='small'
                  onChange={onChangeNewPasswordCheck}
                  error={newPasswordCheckValidation()}
                  helperText={newPasswordCheckValidation() ? '비밀번호가 일치하지 않습니다' : ''}
                  value={newPasswordCheck}
                />
              </Grid>
            </Grid>

            <div>
              <Button
                type='submit'
                fullWidth
                variant='contained'
                sx={{ mt: 5 }}
                size='large'
                color='primary'
                onClick={onSubmit}
              >
                비밀번호 변경
              </Button>
            </div>
          </form>
        </Container>
      </ThemeProvider>
    </div>
  );
}

export default ChangePasswordPage;
